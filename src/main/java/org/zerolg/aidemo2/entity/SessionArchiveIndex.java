package org.zerolg.aidemo2.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话归档索引实体
 * 用途：提供轻量级的历史会话查询能力，避免直接扫描存储了大量 JSON 内容的主表。
 */
@Data
@Builder
@TableName("session_archive_index")
public class SessionArchiveIndex {

    @TableId(type = IdType.INPUT) // 使用 Redis 中的 conversationId 作为主键
    private String conversationId;

    private String userId;

    // 会话摘要 (可由 LLM 生成或截取第一句话)
    private String summary;

    // 消息总数
    private Integer messageCount;

    // 总 Token 消耗
    private Integer totalTokens;

    // 会话开始时间
    private LocalDateTime startTime;

    // 会话最后活跃时间 (即归档触发时间)
    private LocalDateTime lastActiveTime;

    // 归档时间
    private LocalDateTime archivedAt;
}