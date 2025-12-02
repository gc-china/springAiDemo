package org.zerolg.aidemo2.service;

import org.zerolg.aidemo2.model.SessionMessage;
import org.zerolg.aidemo2.model.SessionMetadata;

import java.util.List;

/**
 * 会话记忆服务接口
 * 
 * 职责：
 * 1. 管理会话消息的存储和检索
 * 2. 管理会话元信息
 * 3. 实现滑动窗口策略
 * 4. 提供会话生命周期管理
 * 
 * 设计原则：
 * - 接口与实现分离，便于测试和替换实现
 * - 方法命名清晰，见名知意
 * - 返回值明确，避免 null
 * 
 * @author zerolg
 */
public interface SessionMemoryService {

    /**
     * 保存消息到会话
     * 
     * 操作流程：
     * 1. 将消息添加到 Redis List（RPUSH）
     * 2. 更新会话元信息（消息数、token 数、最后活跃时间）
     * 3. 检查消息数量，超过限制则删除最旧的消息（LTRIM）
     * 4. 刷新会话 TTL（EXPIRE）
     * 
     * @param conversationId 会话 ID
     * @param message        消息对象
     */
    void saveMessage(String conversationId, SessionMessage message);

    /**
     * 获取最近 N 条消息
     * 
     * 使用场景：
     * - 显示对话历史
     * - 构建上下文（简单场景）
     * 
     * 实现：
     * - 使用 LRANGE 命令获取最后 N 条消息
     * - 返回的消息按时间正序排列（最旧的在前）
     * 
     * @param conversationId 会话 ID
     * @param count          消息数量
     * @return 消息列表（按时间正序）
     */
    List<SessionMessage> getRecentMessages(String conversationId, int count);

    /**
     * 按 token 预算获取消息（滑动窗口策略）
     * 
     * 核心算法：
     * 1. 从最新消息开始向前遍历
     * 2. 累加每条消息的 token 数
     * 3. 当累计 token 数达到限制时停止
     * 4. 返回选中的消息列表
     * 
     * 为什么需要：
     * - LLM 有上下文窗口限制（如 GPT-3.5 是 4096 tokens）
     * - 超过限制会导致调用失败或被截断
     * - 合理控制可以降低成本
     * 
     * 优化策略：
     * - 优先保留最近的消息（更相关）
     * - 可以保留系统消息（如果有）
     * - 可以保留重要消息（通过 metadata 标记）
     * 
     * @param conversationId 会话 ID
     * @param maxTokens      最大 token 数
     * @return 消息列表（按时间正序，总 token 数不超过 maxTokens）
     */
    List<SessionMessage> getMessagesByTokenLimit(String conversationId, int maxTokens);

    /**
     * 获取会话元信息
     * 
     * 使用场景：
     * - 检查会话状态
     * - 获取统计信息（消息数、token 数）
     * - 判断会话是否存在
     * 
     * @param conversationId 会话 ID
     * @return 会话元信息，如果会话不存在返回 null
     */
    SessionMetadata getMetadata(String conversationId);

    /**
     * 创建新会话
     * 
     * 操作：
     * 1. 创建会话元信息
     * 2. 保存到 Redis Hash
     * 3. 设置 TTL
     * 
     * @param conversationId 会话 ID
     * @param userId         用户 ID
     */
    void createSession(String conversationId, String userId);

    /**
     * 删除会话
     * 
     * 操作：
     * 1. 删除消息列表（DEL session:messages:{conversationId}）
     * 2. 删除元信息（DEL session:meta:{conversationId}）
     * 
     * 注意：
     * - 这是物理删除，数据无法恢复
     * - 如果需要软删除，使用 archiveSession()
     * 
     * @param conversationId 会话 ID
     */
    void deleteSession(String conversationId);

    /**
     * 归档会话
     * 
     * 操作：
     * 1. 更新会话状态为 "archived"
     * 2. 可选：将数据导出到冷存储（S3/PostgreSQL）
     * 3. 可选：删除 Redis 中的数据（保留元信息）
     * 
     * 使用场景：
     * - 长期不活跃的会话
     * - 需要保留但不常访问的会话
     * 
     * @param conversationId 会话 ID
     */
    void archiveSession(String conversationId);

    /**
     * 检查会话是否存在
     * 
     * @param conversationId 会话 ID
     * @return true 如果会话存在
     */
    boolean sessionExists(String conversationId);

    /**
     * 刷新会话 TTL
     * 
     * 使用场景：
     * - 每次用户活跃时调用
     * - 防止活跃会话过期
     * 
     * @param conversationId 会话 ID
     */
    void refreshSessionTTL(String conversationId);
}
