package org.zerolg.aidemo2.audit.model;

import java.time.Instant;
import java.util.Map;

/**
 * 会话审计摘要
 */
public record SessionAuditSummary(
        String sessionId,                    // 会话ID
        String userId,                       // 用户ID
        Instant startTime,                   // 开始时间
        Instant endTime,                     // 结束时间
        int totalExecutions,                 // 总执行次数
        int successfulExecutions,            // 成功执行次数
        int failedExecutions,                // 失败执行次数
        int ambiguousExecutions,             // 模糊执行次数
        Map<String, Integer> toolUsageCount, // 工具使用统计
        double averageExecutionTime,         // 平均执行时间
        int totalParameterTransformations,   // 总参数转换次数
        double averageConfidence             // 平均置信度
) {
    public static SessionAuditSummary empty(String sessionId, String userId) {
        return new SessionAuditSummary(
                sessionId,
                userId,
                Instant.now(),
                Instant.now(),
                0, 0, 0, 0,
                Map.of(),
                0.0,
                0,
                0.0
        );
    }
}