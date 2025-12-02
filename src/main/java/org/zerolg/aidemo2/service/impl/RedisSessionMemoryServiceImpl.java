package org.zerolg.aidemo2.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.zerolg.aidemo2.model.SessionMessage;
import org.zerolg.aidemo2.model.SessionMetadata;
import org.zerolg.aidemo2.properties.SessionProperties;
import org.zerolg.aidemo2.service.SessionMemoryService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 会话记忆服务实现类（基于 Redis）
 * 
 * 数据结构设计：
 * 1. 消息列表：Redis List
 *    Key: session:messages:{conversationId}
 *    Value: JSON 序列化的 SessionMessage 对象
 *    操作：RPUSH（追加）、LRANGE（查询）、LTRIM（清理）
 * 
 * 2. 会话元信息：Redis Hash
 *    Key: session:meta:{conversationId}
 *    Fields: userId, createdAt, lastActiveAt, messageCount, totalTokens, status
 *    操作：HSET（设置）、HGETALL（查询）、HINCRBY（递增）
 * 
 * 为什么这样设计：
 * - List 适合存储有序的消息历史，支持范围查询
 * - Hash 适合存储结构化的元信息，支持字段级更新
 * - 分离存储便于独立管理和优化
 * 
 * 性能优化：
 * - 使用连接池复用连接
 * - 批量操作减少网络往返
 * - 合理设置 TTL 自动清理过期数据
 * 
 * @author zerolg
 */
@Service
public class RedisSessionMemoryServiceImpl implements SessionMemoryService {

    private static final Logger logger = LoggerFactory.getLogger(RedisSessionMemoryServiceImpl.class);

    /**
     * Redis Key 前缀
     * 
     * 为什么使用前缀：
     * - 命名空间隔离，避免 Key 冲突
     * - 便于管理和监控（可以按前缀查询）
     * - 便于批量删除（KEYS session:* 或 SCAN）
     */
    private static final String MESSAGE_KEY_PREFIX = "session:messages:";
    private static final String META_KEY_PREFIX = "session:meta:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final SessionProperties sessionProperties;
    private final ObjectMapper objectMapper;

    /**
     * 构造函数注入依赖
     * 
     * Spring 会自动注入这些 Bean：
     * - redisTemplate: 在 RedisConfig 中配置
     * - sessionProperties: 在 SessionProperties 中配置
     * - objectMapper: 在 RedisConfig 中配置
     */
    public RedisSessionMemoryServiceImpl(
            RedisTemplate<String, Object> redisTemplate,
            SessionProperties sessionProperties,
            ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.sessionProperties = sessionProperties;
        this.objectMapper = objectMapper;
        
        logger.info("SessionMemoryService 初始化完成，配置: {}", sessionProperties);
    }

    /**
     * 保存消息到会话
     * 
     * 实现步骤：
     * 1. 构建 Redis Key
     * 2. 将消息序列化为 JSON
     * 3. 追加到 List 末尾（RPUSH）
     * 4. 更新元信息
     * 5. 检查并清理超出限制的消息
     * 6. 刷新 TTL
     */
    @Override
    public void saveMessage(String conversationId, SessionMessage message) {
        try {
            logger.debug("保存消息到会话: conversationId={}, messageId={}", conversationId, message.id());
            
            // 1. 构建 Key
            String messageKey = MESSAGE_KEY_PREFIX + conversationId;
            String metaKey = META_KEY_PREFIX + conversationId;
            
            // 2. 序列化消息为 JSON
            // 为什么手动序列化：
            // - RedisTemplate 的序列化器会添加类型信息，导致数据冗余
            // - 手动序列化可以精确控制格式
            String messageJson = objectMapper.writeValueAsString(message);
            
            // 3. 追加消息到 List 末尾
            // RPUSH: 从右侧（末尾）插入，保证消息按时间顺序排列
            // 返回值是插入后的列表长度
            Long listSize = redisTemplate.opsForList().rightPush(messageKey, messageJson);
            
            logger.debug("消息已追加，当前列表长度: {}", listSize);
            
            // 4. 更新元信息
            // 如果元信息不存在，先创建
            if (!sessionExists(conversationId)) {
                // 从消息的 metadata 中获取 userId（如果有）
                String userId = (String) message.metadata().getOrDefault("userId", "unknown");
                createSession(conversationId, userId);
            }
            
            // 递增消息计数和 token 数
            // HINCRBY: 原子递增，线程安全
            redisTemplate.opsForHash().increment(metaKey, "messageCount", 1);
            redisTemplate.opsForHash().increment(metaKey, "totalTokens", message.tokens());
            
            // 更新最后活跃时间
            redisTemplate.opsForHash().put(metaKey, "lastActiveAt", System.currentTimeMillis());
            
            // 5. 检查消息数量，超过限制则清理
            if (listSize != null && listSize > sessionProperties.getMaxMessages()) {
                // LTRIM: 保留指定范围的元素，删除其他元素
                // 保留最后 maxMessages 条消息
                // 例如：maxMessages=100，listSize=105
                // 保留索引 5 到 104（共 100 条）
                long start = listSize - sessionProperties.getMaxMessages();
                long end = -1; // -1 表示列表末尾
                redisTemplate.opsForList().trim(messageKey, start, end);
                
                logger.info("会话消息超过限制，已清理旧消息: conversationId={}, 保留数量={}", 
                        conversationId, sessionProperties.getMaxMessages());
            }
            
            // 6. 刷新 TTL
            refreshSessionTTL(conversationId);
            
            logger.debug("消息保存成功: conversationId={}", conversationId);
            
        } catch (JsonProcessingException e) {
            logger.error("消息序列化失败: conversationId={}, message={}", conversationId, message, e);
            throw new RuntimeException("消息序列化失败", e);
        } catch (Exception e) {
            logger.error("保存消息失败: conversationId={}", conversationId, e);
            throw new RuntimeException("保存消息失败", e);
        }
    }

    /**
     * 获取最近 N 条消息
     * 
     * 实现步骤：
     * 1. 构建 Redis Key
     * 2. 使用 LRANGE 获取最后 N 条消息
     * 3. 反序列化 JSON 为对象
     * 4. 返回消息列表
     */
    @Override
    public List<SessionMessage> getRecentMessages(String conversationId, int count) {
        try {
            logger.debug("获取最近消息: conversationId={}, count={}", conversationId, count);
            
            String messageKey = MESSAGE_KEY_PREFIX + conversationId;
            
            // LRANGE: 获取列表指定范围的元素
            // -count 表示从倒数第 count 个元素开始
            // -1 表示到列表末尾
            // 例如：count=10，获取最后 10 条消息
            List<Object> messageJsonList = redisTemplate.opsForList().range(messageKey, -count, -1);
            
            if (messageJsonList == null || messageJsonList.isEmpty()) {
                logger.debug("会话无消息: conversationId={}", conversationId);
                return Collections.emptyList();
            }
            
            // 反序列化 JSON 为对象
            List<SessionMessage> messages = new ArrayList<>();
            for (Object messageJson : messageJsonList) {
                try {
                    SessionMessage message = objectMapper.readValue(
                            messageJson.toString(), 
                            SessionMessage.class
                    );
                    messages.add(message);
                } catch (JsonProcessingException e) {
                    logger.error("消息反序列化失败: json={}", messageJson, e);
                    // 跳过损坏的消息，继续处理其他消息
                }
            }
            
            logger.debug("获取到 {} 条消息", messages.size());
            return messages;
            
        } catch (Exception e) {
            logger.error("获取消息失败: conversationId={}", conversationId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 按 token 预算获取消息（滑动窗口策略）
     * 
     * 核心算法：
     * 1. 获取所有消息（或最近 N 条）
     * 2. 从最新消息开始向前遍历
     * 3. 累加 token 数，直到达到限制
     * 4. 返回选中的消息
     * 
     * 优化：
     * - 可以先获取最近 50-100 条消息，而不是全部
     * - 可以缓存计算结果（如果消息没有变化）
     */
    @Override
    public List<SessionMessage> getMessagesByTokenLimit(String conversationId, int maxTokens) {
        try {
            logger.debug("按 token 限制获取消息: conversationId={}, maxTokens={}", conversationId, maxTokens);
            
            // 1. 获取最近的消息（假设最近 100 条足够）
            // 如果需要更多，可以增加这个数量
            List<SessionMessage> allMessages = getRecentMessages(conversationId, 100);
            
            if (allMessages.isEmpty()) {
                return Collections.emptyList();
            }
            
            // 2. 从最新消息开始向前遍历
            List<SessionMessage> selectedMessages = new ArrayList<>();
            int totalTokens = 0;
            
            // 倒序遍历（从最新到最旧）
            for (int i = allMessages.size() - 1; i >= 0; i--) {
                SessionMessage message = allMessages.get(i);
                
                // 检查是否超过 token 限制
                if (totalTokens + message.tokens() > maxTokens) {
                    // 如果加上这条消息会超过限制，停止
                    logger.debug("达到 token 限制，停止添加消息: totalTokens={}, messageTokens={}, limit={}", 
                            totalTokens, message.tokens(), maxTokens);
                    break;
                }
                
                // 添加消息到结果列表（头部插入，保持正序）
                selectedMessages.add(0, message);
                totalTokens += message.tokens();
            }
            
            logger.debug("选中 {} 条消息，总 token 数: {}", selectedMessages.size(), totalTokens);
            return selectedMessages;
            
        } catch (Exception e) {
            logger.error("按 token 限制获取消息失败: conversationId={}", conversationId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取会话元信息
     * 
     * 实现步骤：
     * 1. 构建 Redis Key
     * 2. 使用 HGETALL 获取所有字段
     * 3. 转换为 SessionMetadata 对象
     */
    @Override
    public SessionMetadata getMetadata(String conversationId) {
        try {
            String metaKey = META_KEY_PREFIX + conversationId;
            
            // HGETALL: 获取 Hash 的所有字段和值
            // 返回 Map<String, Object>
            var metaMap = redisTemplate.opsForHash().entries(metaKey);
            
            if (metaMap.isEmpty()) {
                logger.debug("会话元信息不存在: conversationId={}", conversationId);
                return null;
            }
            
            // 转换为 SessionMetadata 对象
            // 注意：Redis 返回的数值类型可能是 String，需要转换
            String userId = (String) metaMap.get("userId");
            long createdAt = Long.parseLong(metaMap.get("createdAt").toString());
            long lastActiveAt = Long.parseLong(metaMap.get("lastActiveAt").toString());
            int messageCount = Integer.parseInt(metaMap.get("messageCount").toString());
            int totalTokens = Integer.parseInt(metaMap.get("totalTokens").toString());
            String status = (String) metaMap.get("status");
            
            return new SessionMetadata(
                    userId,
                    createdAt,
                    lastActiveAt,
                    messageCount,
                    totalTokens,
                    status
            );
            
        } catch (Exception e) {
            logger.error("获取会话元信息失败: conversationId={}", conversationId, e);
            return null;
        }
    }

    /**
     * 创建新会话
     */
    @Override
    public void createSession(String conversationId, String userId) {
        try {
            logger.info("创建新会话: conversationId={}, userId={}", conversationId, userId);
            
            String metaKey = META_KEY_PREFIX + conversationId;
            
            // 创建元信息
            SessionMetadata metadata = SessionMetadata.createNew(userId);
            
            // 保存到 Redis Hash
            // HSET: 设置 Hash 的字段值
            redisTemplate.opsForHash().put(metaKey, "userId", metadata.userId());
            redisTemplate.opsForHash().put(metaKey, "createdAt", metadata.createdAt());
            redisTemplate.opsForHash().put(metaKey, "lastActiveAt", metadata.lastActiveAt());
            redisTemplate.opsForHash().put(metaKey, "messageCount", metadata.messageCount());
            redisTemplate.opsForHash().put(metaKey, "totalTokens", metadata.totalTokens());
            redisTemplate.opsForHash().put(metaKey, "status", metadata.status());
            
            // 设置 TTL
            redisTemplate.expire(metaKey, sessionProperties.getTtl(), TimeUnit.SECONDS);
            
            logger.info("会话创建成功: conversationId={}", conversationId);
            
        } catch (Exception e) {
            logger.error("创建会话失败: conversationId={}", conversationId, e);
            throw new RuntimeException("创建会话失败", e);
        }
    }

    /**
     * 删除会话
     */
    @Override
    public void deleteSession(String conversationId) {
        try {
            logger.info("删除会话: conversationId={}", conversationId);
            
            String messageKey = MESSAGE_KEY_PREFIX + conversationId;
            String metaKey = META_KEY_PREFIX + conversationId;
            
            // DEL: 删除 Key
            redisTemplate.delete(messageKey);
            redisTemplate.delete(metaKey);
            
            logger.info("会话删除成功: conversationId={}", conversationId);
            
        } catch (Exception e) {
            logger.error("删除会话失败: conversationId={}", conversationId, e);
            throw new RuntimeException("删除会话失败", e);
        }
    }

    /**
     * 归档会话
     */
    @Override
    public void archiveSession(String conversationId) {
        try {
            logger.info("归档会话: conversationId={}", conversationId);
            
            String metaKey = META_KEY_PREFIX + conversationId;
            
            // 更新状态为 archived
            redisTemplate.opsForHash().put(metaKey, "status", "archived");
            
            // TODO: 可以在这里实现将数据导出到 S3 或 PostgreSQL
            // 例如：
            // 1. 获取所有消息
            // 2. 序列化为 JSON 或其他格式
            // 3. 上传到 S3 或写入数据库
            // 4. 删除 Redis 中的消息（保留元信息）
            
            logger.info("会话归档成功: conversationId={}", conversationId);
            
        } catch (Exception e) {
            logger.error("归档会话失败: conversationId={}", conversationId, e);
            throw new RuntimeException("归档会话失败", e);
        }
    }

    /**
     * 检查会话是否存在
     */
    @Override
    public boolean sessionExists(String conversationId) {
        String metaKey = META_KEY_PREFIX + conversationId;
        // EXISTS: 检查 Key 是否存在
        Boolean exists = redisTemplate.hasKey(metaKey);
        return exists != null && exists;
    }

    /**
     * 刷新会话 TTL
     */
    @Override
    public void refreshSessionTTL(String conversationId) {
        try {
            String messageKey = MESSAGE_KEY_PREFIX + conversationId;
            String metaKey = META_KEY_PREFIX + conversationId;
            
            // EXPIRE: 设置 Key 的过期时间
            // 每次调用都会重置 TTL
            redisTemplate.expire(messageKey, sessionProperties.getTtl(), TimeUnit.SECONDS);
            redisTemplate.expire(metaKey, sessionProperties.getTtl(), TimeUnit.SECONDS);
            
            logger.debug("会话 TTL 已刷新: conversationId={}, ttl={}秒", conversationId, sessionProperties.getTtl());
            
        } catch (Exception e) {
            logger.error("刷新会话 TTL 失败: conversationId={}", conversationId, e);
        }
    }
}
