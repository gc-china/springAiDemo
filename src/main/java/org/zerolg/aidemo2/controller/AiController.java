package org.zerolg.aidemo2.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.zerolg.aidemo2.constant.RedisKeys;
import org.zerolg.aidemo2.service.AiService;
import org.zerolg.aidemo2.service.memory.SessionArchiveService;
import reactor.core.publisher.Flux;

import java.util.concurrent.TimeUnit;

@RestController
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;
    private final StringRedisTemplate redisTemplate;
    private final SessionArchiveService sessionArchiveService; // 注入归档服务

    /**
     * 最终优化的混合路由流式接口 (Tool Override + 动态工具注册 + 多轮对话)
     * 增加 userId 参数以支持会话归档归属
     */
    @GetMapping(value = "/three-stage/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chat(
            @RequestParam String chatId,
            @RequestParam String msg,
            @RequestParam(required = false, defaultValue = "anonymous") String userId) {

        // 1. 检查并恢复会话 (如果 Redis 没数据但 DB 有，则回捞)
        checkAndReactivateSession(chatId);

        // 2. 维护会话心跳与元数据
        updateHeartbeat(chatId, userId);

        // 3. 执行核心对话逻辑
        return aiService.processQuery(chatId, msg);
    }

    /**
     * 维护会话状态 (Heartbeat + Metadata)
     * 确保归档任务能扫描到活跃会话，并关联正确的用户ID
     */
    private void updateHeartbeat(String conversationId, String userId) {
        long now = System.currentTimeMillis();

        // 1. 更新 ZSET 心跳 (用于过期扫描)
        redisTemplate.opsForZSet().add(RedisKeys.SESSION_HEARTBEAT, conversationId, now);

        // 2. 确保元数据存在 (用于归档时获取 userId)
        String metaKey = RedisKeys.SESSION_META_PREFIX + conversationId;

        if (userId != null && !userId.isBlank()) {
            redisTemplate.opsForHash().put(metaKey, "userId", userId);
        }

        // 3. 刷新元数据 TTL (30天)，防止 ZSET 漏删导致垃圾数据堆积
        redisTemplate.expire(metaKey, 30, TimeUnit.DAYS);
    }

    /**
     * 检查会话是否需要从冷存储中激活
     */
    private void checkAndReactivateSession(String conversationId) {
        String listKey = RedisKeys.SESSION_MSG_PREFIX + conversationId;
        // 如果 Redis 中没有该会话的消息记录
        if (Boolean.FALSE.equals(redisTemplate.hasKey(listKey))) {
            // 尝试从 DB 回捞
            sessionArchiveService.reactivateSession(conversationId);
        }
    }
}