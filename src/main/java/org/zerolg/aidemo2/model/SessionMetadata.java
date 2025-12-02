package org.zerolg.aidemo2.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 会话元信息实体类
 * 
 * 原理说明：
 * 1. 存储会话级别的统计和状态信息
 * 2. 使用 Redis Hash 存储，每个字段对应一个 Hash field
 * 3. 与消息历史分离存储，便于快速查询和更新
 * 
 * 存储结构（Redis Hash）：
 * Key: session:meta:{conversationId}
 * Fields:
 *   - userId: "user-123"
 *   - createdAt: 1701518400000
 *   - lastActiveAt: 1701604800000
 *   - messageCount: 15
 *   - totalTokens: 2500
 *   - status: "active"
 * 
 * 为什么使用 Hash：
 * - 原子更新：可以单独更新某个字段（如 messageCount++）
 * - 节省内存：Hash 比多个独立 Key 更节省内存
 * - 查询效率：HGETALL 一次获取所有字段
 * 
 * @author zerolg
 */
public record SessionMetadata(
        /**
         * 用户 ID
         * 
         * 作用：
         * - 关联用户和会话
         * - 用于多租户隔离
         * - 用于用户级别的统计和限额
         */
        @JsonProperty("userId")
        String userId,

        /**
         * 会话创建时间（毫秒时间戳）
         * 
         * 作用：
         * - 计算会话存活时间
         * - 用于归档策略（如归档 30 天前的会话）
         * - 审计和追溯
         */
        @JsonProperty("createdAt")
        long createdAt,

        /**
         * 最后活跃时间（毫秒时间戳）
         * 
         * 作用：
         * - 判断会话是否活跃
         * - 用于清理策略（如清理 7 天未活跃的会话）
         * - 用于 TTL 刷新（每次活跃时重置 TTL）
         * 
         * 更新时机：
         * - 每次发送消息时更新
         * - 每次接收回复时更新
         */
        @JsonProperty("lastActiveAt")
        long lastActiveAt,

        /**
         * 消息数量
         * 
         * 作用：
         * - 快速获取消息数量（无需 LLEN）
         * - 用于限额控制（如单个会话最多 100 条消息）
         * - 用于统计分析
         * 
         * 更新方式：
         * - 每次添加消息时 +1
         * - 每次删除消息时 -1
         * - 使用 HINCRBY 原子递增
         */
        @JsonProperty("messageCount")
        int messageCount,

        /**
         * 总 token 数
         * 
         * 作用：
         * - 成本估算（LLM 按 token 计费）
         * - 用户级别的 token 配额控制
         * - 统计分析
         * 
         * 计算方式：
         * - 累加所有消息的 token 数
         * - 包括用户输入和 AI 回复
         * 
         * 更新方式：
         * - 每次添加消息时累加
         * - 使用 HINCRBY 原子递增
         */
        @JsonProperty("totalTokens")
        int totalTokens,

        /**
         * 会话状态
         * 
         * 可选值：
         * - "active": 活跃状态，正常使用
         * - "archived": 已归档，只读不写
         * - "deleted": 已删除，等待物理删除
         * 
         * 作用：
         * - 控制会话的生命周期
         * - 归档策略：将长期不活跃的会话归档到冷存储
         * - 删除策略：软删除，标记为 deleted 后定期物理删除
         * 
         * 状态转换：
         * active → archived（归档）
         * active → deleted（删除）
         * archived → deleted（删除归档）
         */
        @JsonProperty("status")
        String status
) {

    /**
     * Jackson 反序列化构造函数
     */
    @JsonCreator
    public SessionMetadata {
        // 参数验证
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("用户 ID 不能为空");
        }
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("会话状态不能为空");
        }
        // 验证状态值
        if (!status.equals("active") && !status.equals("archived") && !status.equals("deleted")) {
            throw new IllegalArgumentException("无效的会话状态: " + status);
        }
    }

    /**
     * 创建新会话元信息的便捷方法
     * 
     * @param userId 用户 ID
     * @return 新的会话元信息对象
     */
    public static SessionMetadata createNew(String userId) {
        long now = System.currentTimeMillis();
        return new SessionMetadata(
                userId,
                now,      // createdAt
                now,      // lastActiveAt
                0,        // messageCount
                0,        // totalTokens
                "active"  // status
        );
    }

    /**
     * 更新最后活跃时间
     * 
     * @return 新的元信息对象（更新了 lastActiveAt）
     */
    public SessionMetadata updateLastActive() {
        return new SessionMetadata(
                this.userId,
                this.createdAt,
                System.currentTimeMillis(), // 更新为当前时间
                this.messageCount,
                this.totalTokens,
                this.status
        );
    }

    /**
     * 增加消息计数和 token 数
     * 
     * @param tokens 新增的 token 数
     * @return 新的元信息对象（更新了计数）
     */
    public SessionMetadata incrementCounts(int tokens) {
        return new SessionMetadata(
                this.userId,
                this.createdAt,
                System.currentTimeMillis(), // 同时更新活跃时间
                this.messageCount + 1,      // 消息数 +1
                this.totalTokens + tokens,  // 累加 token
                this.status
        );
    }

    /**
     * 更新会话状态
     * 
     * @param newStatus 新状态
     * @return 新的元信息对象（更新了状态）
     */
    public SessionMetadata updateStatus(String newStatus) {
        return new SessionMetadata(
                this.userId,
                this.createdAt,
                this.lastActiveAt,
                this.messageCount,
                this.totalTokens,
                newStatus
        );
    }

    /**
     * 检查会话是否活跃
     * 
     * @param inactiveDays 不活跃天数阈值
     * @return true 如果会话在指定天数内活跃
     */
    public boolean isActive(int inactiveDays) {
        long threshold = System.currentTimeMillis() - (inactiveDays * 24L * 60 * 60 * 1000);
        return this.lastActiveAt >= threshold;
    }
}
