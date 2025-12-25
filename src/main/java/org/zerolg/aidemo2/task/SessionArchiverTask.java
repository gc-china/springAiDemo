package org.zerolg.aidemo2.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.zerolg.aidemo2.constant.RedisKeys;
import org.zerolg.aidemo2.service.memory.SessionArchiveService;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 会话归档定时任务
 * 职责：扫描长期不活跃的会话，将其从 Redis 迁移到 PostgreSQL
 */
@Component
@RequiredArgsConstructor
public class SessionArchiverTask {

    private static final Logger logger = LoggerFactory.getLogger(SessionArchiverTask.class);
    // 配置：会话超时时间 (7天)
    private static final long SESSION_TIMEOUT_MS = Duration.ofDays(7).toMillis();
    private final StringRedisTemplate redisTemplate;
    private final SessionArchiveService archiveService;
    private final ObjectMapper objectMapper;

    /**
     * 定时扫描过期会话
     * 频率：每 10 分钟执行一次
     */
    @Scheduled(cron = "0 0/10 * * * ?")
    public void scanAndArchiveExpiredSessions() {
        logger.info(">>> 开始扫描过期会话...");

        long now = System.currentTimeMillis();
        long threshold = now - SESSION_TIMEOUT_MS;

        // 1. 从 ZSET 中找出所有最后活跃时间早于 threshold 的会话 ID
        // ZRANGEBYSCORE key -inf threshold
        Set<String> expiredSessionIds = redisTemplate.opsForZSet()
                .rangeByScore(RedisKeys.SESSION_HEARTBEAT, 0, threshold);

        if (expiredSessionIds == null || expiredSessionIds.isEmpty()) {
            logger.info("<<< 无过期会话需要归档。");
            return;
        }

        logger.info("发现 {} 个过期会话，准备归档...", expiredSessionIds.size());

        for (String conversationId : expiredSessionIds) {
            // Double Check: 乐观锁思想。再次检查最后活跃时间。
            // 防止在扫描间隙用户突然活跃，导致误删新产生的消息。
            Double currentScore = redisTemplate.opsForZSet().score(RedisKeys.SESSION_HEARTBEAT, conversationId);
            if (currentScore != null && currentScore > threshold) {
                logger.info("会话 [{}] 在归档期间变为活跃，跳过本次归档。", conversationId);
                continue;
            }

            try {
                archiveSingleSession(conversationId);

                // 归档成功后，从心跳 ZSET 中移除
                redisTemplate.opsForZSet().remove(RedisKeys.SESSION_HEARTBEAT, conversationId);

            } catch (Exception e) {
                logger.error("归档会话 [{}] 失败，跳过，等待下次重试。", conversationId, e);
                // 注意：这里不移除 ZSET，下次任务会再次扫描到它（实现了简单的重试机制）
            }
        }
    }

    /**
     * 归档单个会话的具体逻辑
     */
    private void archiveSingleSession(String conversationId) throws Exception {
        String sessionKey = RedisKeys.SESSION_MSG_PREFIX + conversationId;
        String metaKey = RedisKeys.SESSION_META_PREFIX + conversationId;

        // 1. 获取 Redis 中的完整消息列表
        List<String> messages = redisTemplate.opsForList().range(sessionKey, 0, -1);

        if (messages == null || messages.isEmpty()) {
            logger.warn("会话 [{}] 在 Redis 中无消息数据，仅清理心跳记录。", conversationId);
            return;
        }

        // 2. 组装成 JSON 数组字符串 (假设 Redis List 里存的已经是 JSON String)
        String contentJson = "[" + String.join(",", messages) + "]";

        // 3. 调用 Service 执行入库 (包含事务)
        // 从 Redis Meta Hash 中获取 userId
        Object userIdObj = redisTemplate.opsForHash().get(metaKey, "userId");
        String userId = (userIdObj != null) ? userIdObj.toString() : "unknown";

        archiveService.archiveSession(conversationId, userId, contentJson, messages.size());

        // 4. 入库成功后，删除 Redis 中的热数据
        // 再次检查 key 是否存在再删除是更安全的操作，但这里直接删也没问题，因为前面做了 Double Check
        redisTemplate.delete(sessionKey);
        // 同时删除元数据
        redisTemplate.delete(metaKey);
    }
}