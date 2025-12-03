package org.zerolg.aidemo2.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.zerolg.aidemo2.model.SessionEvent;
import org.zerolg.aidemo2.model.SessionMessage;
import org.zerolg.aidemo2.model.SessionMetadata;
import org.zerolg.aidemo2.properties.SessionProperties;
import org.zerolg.aidemo2.service.SessionMemoryService;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 会话记忆服务实现类（基于 Redis）
 * 
 * 核心功能：
 * 1. 提供基于 Redis List 的热数据存储，保证对话上下文的快速读写。
 * 2. 集成 Redis Stream，将所有会话事件（如消息创建）作为不可变日志发布，用于异步归档和审计。
 * 3. 维护会话元数据（Token 计数、最后活跃时间等）。
 */
@Service
public class RedisSessionMemoryServiceImpl implements SessionMemoryService {

    private static final Logger logger = LoggerFactory.getLogger(RedisSessionMemoryServiceImpl.class);

    // Redis Key 前缀定义
    private static final String MESSAGE_KEY_PREFIX = "session:messages:"; // 消息列表 Key 前缀
    private static final String META_KEY_PREFIX = "session:meta:"; // 元数据 Hash Key 前缀
    private static final String STREAM_KEY = "session:event:stream"; // 事件流 Key (全局唯一)

    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final SessionProperties sessionProperties;
    private final ObjectMapper objectMapper;

    public RedisSessionMemoryServiceImpl(RedisTemplate<String, Object> redisTemplate,
            StringRedisTemplate stringRedisTemplate,
            SessionProperties sessionProperties,
            ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        this.sessionProperties = sessionProperties;
        this.objectMapper = objectMapper;
        logger.info("SessionMemoryService 初始化完成，配置: {}", sessionProperties);
    }

    /**
     * 保存消息到会话
     * 
     * 采用混合写入策略：
     * 1. 同步写入 Redis List：确保当前对话上下文立即可用（Read-Your-Writes）。
     * 2. 同步发布到 Redis Stream：确保事件被可靠记录，供异步归档服务消费。
     */
    @Override
    public void saveMessage(String conversationId, SessionMessage message) {
        try {
            logger.debug("保存消息到会话: conversationId={}, messageId={}", conversationId, message.id());

            String messageKey = MESSAGE_KEY_PREFIX + conversationId;
            String metaKey = META_KEY_PREFIX + conversationId;

            // 将消息对象序列化为 JSON 字符串
            String messageJson = objectMapper.writeValueAsString(message);

            // 1. 保存到 List (热数据)
            // 使用 RPUSH 将新消息追加到列表末尾，保持时间顺序
            Long listSize = redisTemplate.opsForList().rightPush(messageKey, messageJson);

            // 2. 发布到 Stream (可靠日志)
            // 将消息封装为事件，发布到 Redis Stream，用于后续的异步归档和审计
            publishToStream(conversationId, message);

            // 3. 更新元数据
            // 如果会话不存在，先创建会话元数据
            if (!sessionExists(conversationId)) {
                String userId = (String) message.metadata().getOrDefault("userId", "unknown");
                createSession(conversationId, userId);
            }

            // 原子递增消息数量和 Token 总数
            redisTemplate.opsForHash().increment(metaKey, "messageCount", 1);
            redisTemplate.opsForHash().increment(metaKey, "totalTokens", message.tokens());
            // 更新最后活跃时间戳
            redisTemplate.opsForHash().put(metaKey, "lastActiveAt", System.currentTimeMillis());

            // 4. 裁剪消息列表 (滑动窗口)
            // 如果消息数量超过配置的最大限制，删除最旧的消息，只保留最近的 N 条
            if (listSize != null && listSize > sessionProperties.getMaxMessages()) {
                long start = listSize - sessionProperties.getMaxMessages();
                // LTRIM: 保留指定范围内的元素，删除其他元素
                redisTemplate.opsForList().trim(messageKey, start, -1);
                logger.info("会话消息已裁剪: conversationId={}, 保留数量={}", conversationId, sessionProperties.getMaxMessages());
            }

            // 5. 刷新 TTL (生存时间)
            // 每次活跃都重置过期时间，防止活跃会话被清除
            refreshSessionTTL(conversationId);

        } catch (JsonProcessingException e) {
            logger.error("消息序列化失败", e);
            throw new RuntimeException("消息序列化失败", e);
        } catch (Exception e) {
            logger.error("保存消息失败", e);
            throw new RuntimeException("保存消息失败", e);
        }
    }

    /**
     * 将会话消息发布到 Redis Stream
     */
    private void publishToStream(String conversationId, SessionMessage message) {
        try {
            // 构建会话事件对象
            SessionEvent event = new SessionEvent();
            event.setEventId(UUID.randomUUID().toString()); // 生成唯一事件 ID
            event.setConversationId(conversationId);
            event.setType("MESSAGE_CREATED"); // 事件类型：消息创建
            event.setTimestamp(Instant.now()); // 事件发生时间

            // 将消息内容转换为 Map 作为事件负载
            Map<String, Object> messageMap = objectMapper.convertValue(message, Map.class);
            Map<String, Object> payload = new HashMap<>();
            payload.put("message", messageMap);
            event.setPayload(payload);

            // 序列化事件对象
            String eventJson = objectMapper.writeValueAsString(event);

            // 写入 Redis Stream
            // StreamRecords.newRecord() 创建一条新记录
            // .ofObject(eventJson) 设置记录内容
            // .withStreamKey(STREAM_KEY) 指定 Stream 的 Key
            stringRedisTemplate.opsForStream().add(
                    StreamRecords.newRecord()
                            .ofObject(eventJson)
                            .withStreamKey(STREAM_KEY));
        } catch (Exception e) {
            // Stream 发布失败不应阻塞主流程，记录错误日志即可
            // 在生产环境中，这里可以加入重试机制或降级策略
            logger.error("发布会话事件到 Stream 失败 (非阻塞)", e);
        }
    }

    /**
     * 获取最近的 N 条消息
     */
    @Override
    public List<SessionMessage> getRecentMessages(String conversationId, int count) {
        try {
            String messageKey = MESSAGE_KEY_PREFIX + conversationId;
            // LRANGE: 获取列表指定范围的元素，-count 表示倒数第 count 个
            List<Object> messageJsonList = redisTemplate.opsForList().range(messageKey, -count, -1);

            if (messageJsonList == null || messageJsonList.isEmpty()) {
                return Collections.emptyList();
            }

            List<SessionMessage> messages = new ArrayList<>();
            for (Object json : messageJsonList) {
                try {
                    // 反序列化 JSON 为 SessionMessage 对象
                    messages.add(objectMapper.readValue(json.toString(), SessionMessage.class));
                } catch (Exception e) {
                    logger.error("反序列化消息失败", e);
                }
            }
            return messages;
        } catch (Exception e) {
            logger.error("获取最近消息失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 按 Token 限制获取消息（智能滑动窗口）
     * 从最新的消息开始回溯，直到累计 Token 数达到限制。
     */
    @Override
    public List<SessionMessage> getMessagesByTokenLimit(String conversationId, int maxTokens) {
        // 1. 先获取最近的 100 条消息（假设足够覆盖上下文窗口）
        List<SessionMessage> allMessages = getRecentMessages(conversationId, 100);
        if (allMessages.isEmpty())
            return Collections.emptyList();

        List<SessionMessage> selected = new ArrayList<>();
        int currentTokens = 0;

        // 2. 倒序遍历（从最新到最旧）
        for (int i = allMessages.size() - 1; i >= 0; i--) {
            SessionMessage msg = allMessages.get(i);
            // 如果加上这条消息会超过最大 Token 限制，则停止添加
            if (currentTokens + msg.tokens() > maxTokens)
                break;

            // 将消息插入到列表头部，保持时间正序
            selected.add(0, msg);
            currentTokens += msg.tokens();
        }
        return selected;
    }

    /**
     * 获取会话元数据
     */
    @Override
    public SessionMetadata getMetadata(String conversationId) {
        try {
            String metaKey = META_KEY_PREFIX + conversationId;
            // HGETALL: 获取 Hash 中的所有字段
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(metaKey);

            if (entries.isEmpty())
                return null;

            // 构建并返回 SessionMetadata 对象
            return new SessionMetadata(
                    (String) entries.get("userId"),
                    Long.parseLong(entries.get("createdAt").toString()),
                    Long.parseLong(entries.get("lastActiveAt").toString()),
                    Integer.parseInt(entries.get("messageCount").toString()),
                    Integer.parseInt(entries.get("totalTokens").toString()),
                    (String) entries.get("status"));
        } catch (Exception e) {
            logger.error("获取元数据失败", e);
            return null;
        }
    }

    /**
     * 创建新会话
     */
    @Override
    public void createSession(String conversationId, String userId) {
        try {
            String metaKey = META_KEY_PREFIX + conversationId;
            SessionMetadata metadata = SessionMetadata.createNew(userId);

            // 准备元数据 Map
            Map<String, Object> map = new HashMap<>();
            map.put("userId", metadata.userId());
            map.put("createdAt", metadata.createdAt());
            map.put("lastActiveAt", metadata.lastActiveAt());
            map.put("messageCount", metadata.messageCount());
            map.put("totalTokens", metadata.totalTokens());
            map.put("status", metadata.status());

            // HMSET: 批量设置 Hash 字段
            redisTemplate.opsForHash().putAll(metaKey, map);
            // 设置过期时间
            redisTemplate.expire(metaKey, sessionProperties.getTtl(), TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.error("创建会话失败", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 删除会话（物理删除）
     */
    @Override
    public void deleteSession(String conversationId) {
        // 删除消息列表和元数据
        redisTemplate.delete(MESSAGE_KEY_PREFIX + conversationId);
        redisTemplate.delete(META_KEY_PREFIX + conversationId);
    }

    /**
     * 归档会话
     * 标记会话状态为 archived。实际的数据迁移由 Stream 消费者异步处理。
     */
    @Override
    public void archiveSession(String conversationId) {
        redisTemplate.opsForHash().put(META_KEY_PREFIX + conversationId, "status", "archived");
    }

    /**
     * 检查会话是否存在
     */
    @Override
    public boolean sessionExists(String conversationId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(META_KEY_PREFIX + conversationId));
    }

    /**
     * 刷新会话 TTL
     */
    @Override
    public void refreshSessionTTL(String conversationId) {
        long ttl = sessionProperties.getTtl();
        // 同时刷新消息列表和元数据的过期时间
        redisTemplate.expire(MESSAGE_KEY_PREFIX + conversationId, ttl, TimeUnit.SECONDS);
        redisTemplate.expire(META_KEY_PREFIX + conversationId, ttl, TimeUnit.SECONDS);
    }
}
