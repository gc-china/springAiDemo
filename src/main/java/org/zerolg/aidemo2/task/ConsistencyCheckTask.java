package org.zerolg.aidemo2.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.zerolg.aidemo2.constant.RedisKeys;
import org.zerolg.aidemo2.entity.SessionArchive;
import org.zerolg.aidemo2.mapper.SessionArchiveMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 数据一致性校验任务
 * 职责：定期检查 Redis(热) 和 DB(冷) 之间的数据一致性，以及 Redis 内部数据的完整性。
 */
@Component
@RequiredArgsConstructor
public class ConsistencyCheckTask {

    private static final Logger logger = LoggerFactory.getLogger(ConsistencyCheckTask.class);
    private final StringRedisTemplate redisTemplate;
    private final SessionArchiveMapper sessionArchiveMapper;

    /**
     * 每天凌晨 3 点执行一次全量/抽样校验
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void checkConsistency() {
        logger.info(">>> 开始执行数据一致性校验...");
        checkDualExistence();
        checkRedisIntegrity();
        logger.info("<<< 数据一致性校验完成。");
    }

    /**
     * 校验 1: "脑裂"检测 (Dual Existence)
     * 场景：同一个会话 ID 既出现在 Redis 热区，又出现在 DB 冷区。
     * 预期：不应该发生。如果发生，通常意味着归档后 Redis 删除失败，或回捞后 DB 删除失败。
     */
    private void checkDualExistence() {
        // 使用 SCAN 遍历 ZSET，避免阻塞 Redis
        long count = 0;
        long errorCount = 0;

        try (Cursor<ZSetOperations.TypedTuple<String>> cursor = redisTemplate.opsForZSet().scan(RedisKeys.SESSION_HEARTBEAT, ScanOptions.scanOptions().match("*").count(1000).build())) {
            List<String> batchIds = new ArrayList<>();

            while (cursor.hasNext()) {
                ZSetOperations.TypedTuple<String> tuple = cursor.next();
                String conversationId = tuple.getValue();
                if (conversationId != null) {
                    batchIds.add(conversationId);
                }

                // 每 100 个 ID 查一次 DB，减少数据库 IO 次数
                if (batchIds.size() >= 100) {
                    errorCount += verifyBatchInDb(batchIds);
                    count += batchIds.size();
                    batchIds.clear();
                }
            }

            // 处理剩余的 ID
            if (!batchIds.isEmpty()) {
                errorCount += verifyBatchInDb(batchIds);
                count += batchIds.size();
            }
        } catch (Exception e) {
            logger.error("一致性校验(脑裂检测)异常", e);
        }

        if (errorCount > 0) {
            logger.error("🚨 [一致性告警] 发现 {} 个会话同时存在于 Redis 和 DB 中 (脑裂)!", errorCount);
        } else {
            logger.info("脑裂检测通过，扫描活跃会话 {} 个，未发现异常。", count);
        }
    }

    private long verifyBatchInDb(List<String> conversationIds) {
        // 查询 DB 中是否存在这些 ID
        Long dbCount = sessionArchiveMapper.selectCount(new LambdaQueryWrapper<SessionArchive>()
                .in(SessionArchive::getConversationId, conversationIds));

        if (dbCount != null && dbCount > 0) {
            // 进一步找出具体是哪些 ID (为了日志记录)
            List<SessionArchive> duplicates = sessionArchiveMapper.selectList(new LambdaQueryWrapper<SessionArchive>()
                    .select(SessionArchive::getConversationId)
                    .in(SessionArchive::getConversationId, conversationIds));

            for (SessionArchive dup : duplicates) {
                logger.warn("⚠️ 发现数据不一致: 会话 [{}] 同时存在于热存储和冷存储中。", dup.getConversationId());
            }
            return duplicates.size();
        }
        return 0;
    }

    /**
     * 校验 2: Redis 内部完整性 (Orphan Check)
     * 场景：ZSET 中有心跳，但 Message List 或 Meta Hash 丢失。
     * 这里只做简单抽样检查，避免全量扫描太慢。
     */
    private void checkRedisIntegrity() {
        // 随机抽查最近活跃的 50 个会话
        Set<String> recentIds = redisTemplate.opsForZSet().reverseRange(RedisKeys.SESSION_HEARTBEAT, 0, 50);
        if (recentIds == null) return;

        for (String id : recentIds) {
            String listKey = RedisKeys.SESSION_MSG_PREFIX + id;
            String metaKey = RedisKeys.SESSION_META_PREFIX + id;

            boolean hasList = Boolean.TRUE.equals(redisTemplate.hasKey(listKey));
            boolean hasMeta = Boolean.TRUE.equals(redisTemplate.hasKey(metaKey));

            if (!hasList) {
                logger.warn("⚠️ 发现孤儿会话 [{}]: 有心跳但无消息列表。", id);
            }
            if (!hasMeta) {
                logger.warn("⚠️ 发现元数据缺失 [{}]: 有心跳但无 Meta Hash。", id);
            }
        }
    }
}