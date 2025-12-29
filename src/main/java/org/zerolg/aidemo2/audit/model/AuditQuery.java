package org.zerolg.aidemo2.audit.model;

import java.time.Instant;
import java.util.List;

/**
 * 审计查询条件
 */
public record AuditQuery(
        String sessionId,           // 会话ID
        String userId,              // 用户ID
        String toolName,            // 工具名称
        List<String> statuses,      // 状态列表
        Instant startTime,          // 开始时间
        Instant endTime,            // 结束时间
        int limit,                  // 限制数量
        int offset                  // 偏移量
) {
    public static AuditQuery forSession(String sessionId) {
        return new AuditQuery(sessionId, null, null, null, null, null, 100, 0);
    }

    public static AuditQuery forUser(String userId) {
        return new AuditQuery(null, userId, null, null, null, null, 100, 0);
    }

    public static AuditQuery forTool(String toolName) {
        return new AuditQuery(null, null, toolName, null, null, null, 100, 0);
    }

    public static AuditQuery forTimeRange(Instant startTime, Instant endTime) {
        return new AuditQuery(null, null, null, null, startTime, endTime, 100, 0);
    }

    public AuditQuery withLimit(int limit) {
        return new AuditQuery(sessionId, userId, toolName, statuses, startTime, endTime, limit, offset);
    }

    public AuditQuery withOffset(int offset) {
        return new AuditQuery(sessionId, userId, toolName, statuses, startTime, endTime, limit, offset);
    }
}