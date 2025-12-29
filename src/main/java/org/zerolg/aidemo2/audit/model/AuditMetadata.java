package org.zerolg.aidemo2.audit.model;

import java.time.Instant;
import java.util.Map;

/**
 * 审计元数据
 */
public record AuditMetadata(
        String executionId,           // 执行唯一标识
        String traceId,              // 分布式追踪ID
        String sessionId,            // 会话ID
        String userId,               // 用户ID
        Instant timestamp,           // 执行时间戳
        String toolName,             // 工具名称
        String methodName,           // 方法名称
        Map<String, Object> context  // 执行上下文
) {
    public static AuditMetadata create(String executionId, String traceId, String sessionId,
                                       String userId, String toolName, String methodName) {
        return new AuditMetadata(
                executionId,
                traceId,
                sessionId,
                userId,
                Instant.now(),
                toolName,
                methodName,
                Map.of()
        );
    }

    public AuditMetadata withContext(Map<String, Object> context) {
        return new AuditMetadata(
                executionId,
                traceId,
                sessionId,
                userId,
                timestamp,
                toolName,
                methodName,
                context
        );
    }
}