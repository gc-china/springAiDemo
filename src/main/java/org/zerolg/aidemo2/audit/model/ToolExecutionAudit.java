package org.zerolg.aidemo2.audit.model;

import java.time.Instant;
import java.util.Map;

/**
 * 工具执行审计记录
 */
public record ToolExecutionAudit(
        String id,                        // 审计记录ID
        String executionId,               // 执行ID
        String traceId,                   // 追踪ID
        String sessionId,                 // 会话ID
        String userId,                    // 用户ID
        String toolName,                  // 工具名称
        String methodName,                // 方法名称
        Map<String, Object> originalParams,    // 原始参数
        Map<String, Object> finalParams,       // 最终参数
        String status,                    // 执行状态
        Object result,                    // 执行结果
        String errorMessage,              // 错误信息
        Instant startTime,                // 开始时间
        Instant endTime,                  // 结束时间
        long executionTimeMs,             // 执行时间（毫秒）
        Map<String, Object> context,      // 执行上下文
        ParameterChain parameterChain,    // 参数转换链
        DecisionContext decisionContext,  // 决策上下文
        PerformanceMetrics metrics        // 性能指标
) {
    public static ToolExecutionAudit create(String executionId, String traceId, String sessionId,
                                            String userId, String toolName, String methodName,
                                            Map<String, Object> originalParams) {
        return new ToolExecutionAudit(
                java.util.UUID.randomUUID().toString(),
                executionId,
                traceId,
                sessionId,
                userId,
                toolName,
                methodName,
                originalParams,
                originalParams,
                "started",
                null,
                null,
                Instant.now(),
                null,
                0,
                Map.of(),
                null,
                null,
                null
        );
    }

    public ToolExecutionAudit withCompletion(String status, Object result, String errorMessage,
                                             Map<String, Object> finalParams, long executionTimeMs) {
        return new ToolExecutionAudit(
                id,
                executionId,
                traceId,
                sessionId,
                userId,
                toolName,
                methodName,
                originalParams,
                finalParams,
                status,
                result,
                errorMessage,
                startTime,
                Instant.now(),
                executionTimeMs,
                context,
                parameterChain,
                decisionContext,
                metrics
        );
    }

    public ToolExecutionAudit withParameterChain(ParameterChain parameterChain) {
        return new ToolExecutionAudit(
                id, executionId, traceId, sessionId, userId, toolName, methodName,
                originalParams, finalParams, status, result, errorMessage,
                startTime, endTime, executionTimeMs, context, parameterChain,
                decisionContext, metrics
        );
    }

    public ToolExecutionAudit withDecisionContext(DecisionContext decisionContext) {
        return new ToolExecutionAudit(
                id, executionId, traceId, sessionId, userId, toolName, methodName,
                originalParams, finalParams, status, result, errorMessage,
                startTime, endTime, executionTimeMs, context, parameterChain,
                decisionContext, metrics
        );
    }

    public ToolExecutionAudit withMetrics(PerformanceMetrics metrics) {
        return new ToolExecutionAudit(
                id, executionId, traceId, sessionId, userId, toolName, methodName,
                originalParams, finalParams, status, result, errorMessage,
                startTime, endTime, executionTimeMs, context, parameterChain,
                decisionContext, metrics
        );
    }
}