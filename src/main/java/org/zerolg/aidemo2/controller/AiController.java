package org.zerolg.aidemo2.controller;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.zerolg.aidemo2.constant.RedisKeys;
import org.zerolg.aidemo2.service.AiService;
import org.zerolg.aidemo2.service.SessionMemoryService;
import org.zerolg.aidemo2.service.memory.SessionArchiveService;
import reactor.core.publisher.Flux;

import java.util.concurrent.TimeUnit;

/**
 * AI对话控制器
 * <p>
 * 主要功能：
 * 1. 提供AI对话的HTTP接口
 * 2. 支持流式响应（Server-Sent Events）
 * 3. 管理会话状态和心跳
 * 4. 支持会话记忆的清除和恢复
 * 5. 集成会话归档功能
 * <p>
 * 技术特点：
 * - 使用SSE实现实时流式对话
 * - Redis存储会话状态和消息历史
 * - 支持会话冷热数据分离
 * - 自动会话心跳维护
 * <p>
 * API设计：
 * - GET /api/three-stage/stream: 流式对话接口
 * - DELETE /api/session/{chatId}/memory: 清除会话记忆
 *
 * @author zerolg
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor  // Lombok注解：自动生成包含final字段的构造函数
public class AiController {

    private static final Logger logger = LoggerFactory.getLogger(AiController.class);

    // 依赖注入的服务组件
    private final AiService aiService;                    // AI对话核心服务
    private final StringRedisTemplate redisTemplate;     // Redis操作模板
    private final SessionArchiveService sessionArchiveService; // 会话归档服务
    private final SessionMemoryService sessionMemoryService;   // 会话内存管理服务

    /**
     * 流式AI对话接口
     *
     * 功能说明：
     * 1. 接收用户消息，返回AI回复的流式响应
     * 2. 支持多轮对话，维护对话上下文
     * 3. 自动管理会话状态和心跳
     * 4. 支持会话冷热数据自动切换
     *
     * 流式响应的优势：
     * - 用户可以实时看到AI生成的内容
     * - 减少等待时间，提升用户体验
     * - 支持长文本生成，避免超时
     *
     * 会话管理机制：
     * 1. 检查Redis中是否有会话数据
     * 2. 如果没有，尝试从数据库恢复（冷数据激活）
     * 3. 更新会话心跳，标记为活跃状态
     * 4. 执行AI对话逻辑
     *
     * @param chatId 会话ID，用于标识不同的对话会话
     * @param msg 用户输入的消息内容
     * @param userId 用户ID，用于会话归属管理，默认为"anonymous"
     * @return 流式响应，包含AI回复的文本流
     */
    @GetMapping(value = "/three-stage/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chat(
            @RequestParam String chatId,
            @RequestParam String msg,
            @RequestParam(required = false, defaultValue = "anonymous") String userId) {

        // 1. 检查并恢复会话
        // 如果Redis中没有会话数据，但数据库中有历史记录，则将其恢复到Redis
        // 这实现了冷热数据的自动切换机制
        checkAndReactivateSession(chatId);

        // 2. 维护会话心跳与元数据
        // 更新会话的最后活跃时间，用于后续的归档和清理任务
        updateHeartbeat(chatId, userId);

        // 3. 执行核心对话逻辑
        // 调用AiService处理用户消息，返回流式响应
        return aiService.processQuery(chatId, msg);
    }

    /**
     * 清除会话记忆接口
     * <p>
     * 功能说明：
     * 1. 删除指定会话的所有历史消息
     * 2. 清除会话相关的元数据
     * 3. 用于测试或重置对话场景
     * <p>
     * 使用场景：
     * - 测试工具调用功能时需要清空历史
     * - 用户主动要求清除对话记录
     * - 开发调试时重置会话状态
     *
     * @param chatId 要清除的会话ID
     * @return 操作结果响应
     */
    @DeleteMapping("/session/{chatId}/memory")
    public ResponseEntity<String> clearSessionMemory(@PathVariable String chatId) {
        try {
            // 调用会话内存服务删除会话数据
            sessionMemoryService.deleteSession(chatId);
            logger.info("会话记忆已清除: chatId={}", chatId);
            return ResponseEntity.ok("会话记忆已清除");
        } catch (Exception e) {
            logger.error("清除会话记忆失败: chatId={}", chatId, e);
            return ResponseEntity.internalServerError().body("清除失败: " + e.getMessage());
        }
    }

    /**
     * 维护会话状态（心跳和元数据）
     *
     * 功能说明：
     * 1. 更新会话心跳时间戳，标记会话为活跃状态
     * 2. 维护会话元数据，包括用户ID等信息
     * 3. 设置合理的TTL，防止数据堆积
     *
     * 数据结构设计：
     * - ZSET存储会话心跳：key=session:heartbeat, score=timestamp, member=chatId
     * - HASH存储会话元数据：key=session:meta:{chatId}, field=userId, value=用户ID
     *
     * 为什么需要心跳机制：
     * - 归档任务需要识别活跃和非活跃会话
     * - 清理任务需要知道哪些会话可以删除
     * - 监控系统需要统计活跃会话数量
     *
     * @param conversationId 会话ID
     * @param userId 用户ID
     */
    private void updateHeartbeat(String conversationId, String userId) {
        long now = System.currentTimeMillis();

        // 1. 更新ZSET心跳记录
        // 使用有序集合存储会话心跳，score为时间戳，便于按时间范围查询
        // 归档任务可以通过zrangeByScore查询指定时间范围内的活跃会话
        redisTemplate.opsForZSet().add(RedisKeys.SESSION_HEARTBEAT, conversationId, now);

        // 2. 确保元数据存在
        // 存储会话的关联信息，如用户ID，用于归档时确定数据归属
        String metaKey = RedisKeys.SESSION_META_PREFIX + conversationId;

        if (userId != null && !userId.isBlank()) {
            redisTemplate.opsForHash().put(metaKey, "userId", userId);
        }

        // 3. 刷新元数据TTL（30天）
        // 防止ZSET漏删导致垃圾数据堆积
        // 即使心跳记录被删除，元数据也会自动过期
        redisTemplate.expire(metaKey, 30, TimeUnit.DAYS);
    }

    /**
     * 检查会话是否需要从冷存储中激活
     *
     * 功能说明：
     * 1. 检查Redis中是否存在会话消息记录
     * 2. 如果不存在，尝试从数据库恢复历史消息
     * 3. 实现冷热数据的无缝切换
     *
     * 冷热数据分离策略：
     * - 热数据：存储在Redis中，访问速度快
     * - 冷数据：存储在数据库中，节省内存
     * - 自动切换：根据访问情况动态调整
     *
     * 恢复机制：
     * 1. 从数据库查询历史消息
     * 2. 重建Redis中的消息列表
     * 3. 恢复会话元数据
     * 4. 记录恢复操作日志
     *
     * @param conversationId 会话ID
     */
    private void checkAndReactivateSession(String conversationId) {
        // 构建会话消息列表的Redis key
        String listKey = RedisKeys.SESSION_MSG_PREFIX + conversationId;

        // 检查Redis中是否存在该会话的消息记录
        if (Boolean.FALSE.equals(redisTemplate.hasKey(listKey))) {
            // Redis中没有数据，尝试从数据库回捞
            boolean reactivated = sessionArchiveService.reactivateSession(conversationId);
            if (reactivated) {
                logger.info("会话 [{}] 已从冷存储回捞至 Redis", conversationId);
            }
        }
    }
}