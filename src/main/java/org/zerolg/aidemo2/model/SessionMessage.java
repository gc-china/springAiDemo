package org.zerolg.aidemo2.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 会话消息实体类
 * 
 * 原理说明：
 * 1. 这个类表示一条对话消息，会被序列化为 JSON 存储在 Redis 中
 * 2. 使用 record 类型（Java 14+）简化代码，自动生成构造函数、getter、equals、hashCode
 * 3. 使用 Jackson 注解支持 JSON 序列化/反序列化
 * 
 * 数据流程：
 * Java 对象 → Jackson 序列化 → JSON 字符串 → Redis 存储
 * Redis 读取 → JSON 字符串 → Jackson 反序列化 → Java 对象
 * 
 * 为什么使用 record：
 * - 不可变性：消息一旦创建不应被修改
 * - 简洁性：自动生成样板代码
 * - 线程安全：不可变对象天然线程安全
 * 
 * @author zerolg
 */
public record SessionMessage(
        /**
         * 消息唯一标识
         * 使用 UUID 保证全局唯一性
         */
        @JsonProperty("id")
        String id,

        /**
         * 消息角色
         * 
         * 可选值：
         * - "user": 用户输入
         * - "assistant": AI 助手回复
         * - "system": 系统提示词
         * - "tool": 工具调用结果
         * 
         * 为什么需要角色：
         * - LLM 需要区分不同来源的消息
         * - 不同角色有不同的处理逻辑
         * - 符合 OpenAI/Anthropic 等标准格式
         */
        @JsonProperty("role")
        String role,

        /**
         * 消息内容
         * 实际的文本内容
         */
        @JsonProperty("content")
        String content,

        /**
         * Token 数量
         * 
         * 作用：
         * - 用于计算上下文窗口大小
         * - 用于成本估算（LLM 按 token 计费）
         * - 用于滑动窗口策略（累加 token 直到达到限制）
         * 
         * 如何计算：
         * - 可以使用 tiktoken 库（OpenAI 官方）
         * - 或简单估算：中文约 1.5 字符/token，英文约 4 字符/token
         */
        @JsonProperty("tokens")
        int tokens,

        /**
         * 消息时间戳（毫秒）
         * 使用 Instant.toEpochMilli() 获取
         * 
         * 作用：
         * - 排序消息
         * - 计算会话活跃时间
         * - 审计和追溯
         */
        @JsonProperty("timestamp")
        long timestamp,

        /**
         * 元数据（扩展字段）
         * 
         * 可以存储：
         * - source: 消息来源（web/mobile/api）
         * - toolName: 工具名称（如果是工具调用）
         * - userId: 用户 ID
         * - sessionId: 会话 ID
         * - 其他自定义字段
         * 
         * 为什么使用 Map：
         * - 灵活性：可以动态添加字段
         * - 向后兼容：新增字段不影响旧数据
         */
        @JsonProperty("metadata")
        Map<String, Object> metadata
) {

    /**
     * Jackson 反序列化构造函数
     * 
     * @JsonCreator 告诉 Jackson 使用这个构造函数进行反序列化
     * 
     * 为什么需要：
     * - record 类型的构造函数是隐式的
     * - Jackson 需要明确知道如何映射 JSON 字段到构造函数参数
     */
    @JsonCreator
    public SessionMessage {
        // record 的紧凑构造函数（Compact Constructor）
        // 在这里可以进行参数验证
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("消息 ID 不能为空");
        }
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("消息角色不能为空");
        }
        if (content == null) {
            throw new IllegalArgumentException("消息内容不能为 null");
        }
        // 确保 metadata 不为 null
        if (metadata == null) {
            metadata = new HashMap<>();
        }
    }

    /**
     * 创建用户消息的便捷方法
     * 
     * @param content 消息内容
     * @param tokens  token 数量
     * @return 用户消息对象
     */
    public static SessionMessage createUserMessage(String content, int tokens) {
        return new SessionMessage(
                UUID.randomUUID().toString(),
                "user",
                content,
                tokens,
                Instant.now().toEpochMilli(),
                new HashMap<>()
        );
    }

    /**
     * 创建助手消息的便捷方法
     * 
     * @param content 消息内容
     * @param tokens  token 数量
     * @return 助手消息对象
     */
    public static SessionMessage createAssistantMessage(String content, int tokens) {
        return new SessionMessage(
                UUID.randomUUID().toString(),
                "assistant",
                content,
                tokens,
                Instant.now().toEpochMilli(),
                new HashMap<>()
        );
    }

    /**
     * 创建系统消息的便捷方法
     * 
     * @param content 消息内容
     * @param tokens  token 数量
     * @return 系统消息对象
     */
    public static SessionMessage createSystemMessage(String content, int tokens) {
        return new SessionMessage(
                UUID.randomUUID().toString(),
                "system",
                content,
                tokens,
                Instant.now().toEpochMilli(),
                new HashMap<>()
        );
    }

    /**
     * 添加元数据的便捷方法
     * 
     * 注意：由于 record 是不可变的，这个方法返回一个新对象
     * 
     * @param key   元数据键
     * @param value 元数据值
     * @return 新的消息对象（包含新增的元数据）
     */
    public SessionMessage withMetadata(String key, Object value) {
        Map<String, Object> newMetadata = new HashMap<>(this.metadata);
        newMetadata.put(key, value);
        return new SessionMessage(
                this.id,
                this.role,
                this.content,
                this.tokens,
                this.timestamp,
                newMetadata
        );
    }
}
