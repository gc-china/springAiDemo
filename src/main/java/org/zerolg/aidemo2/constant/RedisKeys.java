// 包声明：定义当前类所属的包路径
package org.zerolg.aidemo2.constant;

/**
 * Redis Key 定义常量类
 *
 * 这个类集中管理所有 Redis 键的命名规范，确保整个应用中 Redis 键的一致性
 *
 * 设计原则：
 * 1. 统一命名：所有 Redis 键都在这里定义，避免硬编码
 * 2. 分类管理：按功能模块分组，便于维护
 * 3. 命名规范：使用冒号分隔的层级结构，如 "aidemo:session:msg:"
 * 4. 类型标识：注释中标明 Redis 数据类型（String/Hash/List/Set/ZSet/Stream）
 *
 * Redis 数据类型说明：
 * - String: 简单的键值对
 * - Hash: 类似 Java 的 HashMap，存储字段-值对
 * - List: 有序列表，支持头尾操作
 * - Set: 无序集合，元素唯一
 * - ZSet: 有序集合，每个元素有分数用于排序
 * - Stream: 消息流，支持消费者组模式
 *
 * 为什么需要这个类：
 * - 避免键名拼写错误
 * - 便于重构和维护
 * - 提供键的文档说明
 * - 统一键的命名规范
 */
public class RedisKeys {

    // ==================== 文档处理相关键 ====================

    /**
     * 文档摄取任务流
     * <p>
     * 数据类型：Stream
     * 用途：处理文档上传、解析、向量化等异步任务
     * <p>
     * Stream 的优势：
     * - 消息持久化：即使消费者离线也不会丢失消息
     * - 消费者组：支持多个消费者并行处理
     * - 消息确认：确保消息被正确处理
     * - 历史回溯：可以重新消费历史消息
     * <p>
     * 消息格式示例：
     * {
     * "taskId": "uuid",
     * "documentId": "doc123",
     * "action": "INGEST|DELETE|UPDATE",
     * "filePath": "/uploads/file.pdf",
     * "timestamp": 1640995200000
     * }
     */
    public static final String STREAM_DOCUMENT_INGESTION = "document:ingestion:stream";

    /**
     * 会话事件流
     * <p>
     * 数据类型：Stream
     * 用途：记录聊天消息、用户行为等会话相关事件
     * <p>
     * 应用场景：
     * - 实时聊天消息推送
     * - 用户行为分析
     * - 会话状态同步
     * - 审计日志记录
     * <p>
     * 消息格式示例：
     * {
     * "eventType": "MESSAGE|JOIN|LEAVE|TYPING",
     * "conversationId": "conv123",
     * "userId": "user456",
     * "content": "用户消息内容",
     * "timestamp": 1640995200000
     * }
     */
    public static final String STREAM_SESSION_EVENT = "session:event:stream";

    // ==================== 会话内存管理键 ====================

    /**
     * 会话心跳索引
     * <p>
     * 数据类型：ZSet (有序集合)
     * 键格式：aidemo:session:heartbeat
     * 分数：时间戳 (Score = Timestamp)
     * 成员：会话ID (Member = ConversationId)
     * <p>
     * 用途：
     * 1. 会话活跃度监控：根据最后活跃时间判断会话是否活跃
     * 2. 会话清理：定期清理长时间不活跃的会话数据
     * 3. 会话排序：按最后活跃时间排序会话列表
     * 4. 负载均衡：将活跃会话分配到不同的处理节点
     * <p>
     * 操作示例：
     * - 更新心跳：ZADD aidemo:session:heartbeat 1640995200000 "conv123"
     * - 获取活跃会话：ZREVRANGE aidemo:session:heartbeat 0 10 WITHSCORES
     * - 清理过期会话：ZREMRANGEBYSCORE aidemo:session:heartbeat 0 (now-timeout)
     * <p>
     * 为什么使用 ZSet：
     * - 自动排序：按时间戳自动排序，无需手动维护
     * - 范围查询：可以高效查询指定时间范围的会话
     * - 原子操作：更新心跳和查询都是原子操作
     */
    public static final String SESSION_HEARTBEAT = "aidemo:session:heartbeat";

    /**
     * 会话消息列表前缀
     *
     * 数据类型：List (列表)
     * 键格式：aidemo:session:msg:{conversationId}
     * 示例键：aidemo:session:msg:conv123
     *
     * 用途：存储单个会话的所有消息，按时间顺序排列
     *
     * 数据结构：
     * - 每个元素是一个 JSON 字符串，包含完整的消息信息
     * - 使用 LPUSH 添加新消息（最新消息在列表头部）
     * - 使用 LRANGE 获取消息历史
     *
     * 消息 JSON 格式：
     * {
     *   "id": "msg-uuid",
     *   "role": "user|assistant|system|tool",
     *   "content": "消息内容",
     *   "tokens": 150,
     *   "timestamp": 1640995200000,
     *   "metadata": {"source": "web", "userId": "user123"}
     * }
     *
     * 操作示例：
     * - 添加消息：LPUSH aidemo:session:msg:conv123 "{json}"
     * - 获取最近10条：LRANGE aidemo:session:msg:conv123 0 9
     * - 获取消息总数：LLEN aidemo:session:msg:conv123
     * - 清理旧消息：LTRIM aidemo:session:msg:conv123 0 99 (保留最近100条)
     *
     * 为什么使用 List：
     * - 有序性：消息需要按时间顺序排列
     * - 高效插入：LPUSH 操作时间复杂度 O(1)
     * - 范围查询：LRANGE 可以高效获取指定范围的消息
     * - 内存优化：可以使用 LTRIM 限制消息数量
     */
    public static final String SESSION_MSG_PREFIX = "aidemo:session:msg:";

    /**
     * 会话元数据前缀
     *
     * 数据类型：Hash (哈希表)
     * 键格式：aidemo:session:meta:{conversationId}
     * 示例键：aidemo:session:meta:conv123
     *
     * 用途：存储会话的元数据信息，如用户ID、开始时间、配置等
     *
     * 字段说明：
     * - userId: 用户ID，标识会话所属用户
     * - startTime: 会话开始时间戳
     * - lastActiveTime: 最后活跃时间戳
     * - messageCount: 消息总数（缓存值，避免频繁 LLEN）
     * - totalTokens: 总 token 数（用于成本统计）
     * - model: 使用的AI模型名称
     * - temperature: 模型温度参数
     * - maxTokens: 最大 token 限制
     * - systemPrompt: 系统提示词
     * - status: 会话状态 (ACTIVE|PAUSED|ENDED)
     * - tags: 会话标签（JSON 数组字符串）
     *
     * 操作示例：
     * - 设置字段：HSET aidemo:session:meta:conv123 userId user123 startTime 1640995200000
     * - 获取字段：HGET aidemo:session:meta:conv123 userId
     * - 获取所有：HGETALL aidemo:session:meta:conv123
     * - 增加计数：HINCRBY aidemo:session:meta:conv123 messageCount 1
     * - 检查存在：HEXISTS aidemo:session:meta:conv123 userId
     *
     * 为什么使用 Hash：
     * - 结构化：字段-值对应，便于管理不同类型的元数据
     * - 原子操作：单个字段的读写都是原子操作
     * - 内存效率：Hash 在字段较少时内存使用更高效
     * - 部分更新：可以只更新特定字段，不影响其他字段
     */
    public static final String SESSION_META_PREFIX = "aidemo:session:meta:";
}