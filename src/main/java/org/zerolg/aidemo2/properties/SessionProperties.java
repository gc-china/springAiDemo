package org.zerolg.aidemo2.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 会话记忆配置属性
 * 
 * 原理说明：
 * 1. 使用 @ConfigurationProperties 自动绑定 application.yml 中的配置
 * 2. 前缀 "session.memory" 对应配置文件中的 session.memory.* 配置项
 * 3. Spring Boot 会自动将配置值注入到对应的字段中
 * 
 * 配置项说明：
 * - ttl: 会话生存时间（秒），超过此时间会话自动过期
 * - maxMessages: 单个会话最大消息数量，超过后自动清理最旧的消息
 * - maxPromptTokens: 提示词最大 token 数，用于滑动窗口策略
 * - defaultRecentCount: 默认返回的最近消息数量
 * 
 * @author zerolg
 */
@Component
@ConfigurationProperties(prefix = "session.memory")
public class SessionProperties {

    /**
     * 会话 TTL（秒）
     * 默认值：604800 秒（7 天）
     * 
     * 作用：Redis 会自动删除超过 TTL 的键，无需手动清理
     */
    private long ttl = 604800L;

    /**
     * 最大消息数量
     * 默认值：100 条
     * 
     * 作用：防止单个会话占用过多内存
     * 实现：当消息数量超过此值时，自动删除最旧的消息（LTRIM 命令）
     */
    private int maxMessages = 100;

    /**
     * 最大 token 预算
     * 默认值：4000 tokens
     * 
     * 作用：控制发送给 LLM 的上下文大小
     * 实现：从最新消息向前累加，直到达到 token 限制
     * 
     * 为什么需要：
     * - LLM 有上下文窗口限制（如 GPT-3.5 是 4096 tokens）
     * - 超过限制会导致调用失败或截断
     * - 合理控制可以降低成本（按 token 计费）
     */
    private int maxPromptTokens = 4000;

    /**
     * 默认返回最近消息数量
     * 默认值：10 条
     * 
     * 作用：当没有指定数量时，返回最近 N 条消息
     */
    private int defaultRecentCount = 10;

    // ==================== Getters and Setters ====================

    public long getTtl() {
        return ttl;
    }

    public void setTtl(long ttl) {
        this.ttl = ttl;
    }

    public int getMaxMessages() {
        return maxMessages;
    }

    public void setMaxMessages(int maxMessages) {
        this.maxMessages = maxMessages;
    }

    public int getMaxPromptTokens() {
        return maxPromptTokens;
    }

    public void setMaxPromptTokens(int maxPromptTokens) {
        this.maxPromptTokens = maxPromptTokens;
    }

    public int getDefaultRecentCount() {
        return defaultRecentCount;
    }

    public void setDefaultRecentCount(int defaultRecentCount) {
        this.defaultRecentCount = defaultRecentCount;
    }

    @Override
    public String toString() {
        return "SessionProperties{" +
                "ttl=" + ttl +
                ", maxMessages=" + maxMessages +
                ", maxPromptTokens=" + maxPromptTokens +
                ", defaultRecentCount=" + defaultRecentCount +
                '}';
    }
}
