package org.zerolg.aidemo2.audit.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.zerolg.aidemo2.audit.model.*;
import org.zerolg.aidemo2.audit.service.AuditService;
import org.zerolg.aidemo2.audit.service.DecisionContextManager;
import org.zerolg.aidemo2.audit.service.ParameterChainRecorder;
import org.zerolg.aidemo2.audit.service.PerformanceMonitor;
import org.zerolg.aidemo2.common.EnhancedToolExecutionResult;
import org.zerolg.aidemo2.common.ToolExecutionResult;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 工具执行审计切面
 * 自动为工具方法添加审计功能
 */
@Aspect
@Component
@ConditionalOnProperty(name = "audit.enabled", havingValue = "true", matchIfMissing = false)
public class ToolExecutionAuditAspect {

    private static final Logger logger = LoggerFactory.getLogger(ToolExecutionAuditAspect.class);

    @Autowired
    private AuditService auditService;

    @Autowired
    private ParameterChainRecorder parameterChainRecorder;

    @Autowired
    private DecisionContextManager decisionContextManager;

    @Autowired
    private PerformanceMonitor performanceMonitor;

    /**
     * 拦截所有返回ToolExecutionResult的工具方法
     */
    @Around("execution(* org.zerolg.aidemo2.tools.*.*(..)) && " +
            "(execution(* *(..) throws *) || execution(org.zerolg.aidemo2.common.ToolExecutionResult *(..)))")
    public Object auditToolExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        String executionId = UUID.randomUUID().toString();
        String traceId = getTraceId();
        String sessionId = getSessionId();
        String userId = getUserId();
        String toolName = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();

        // 提取原始参数
        Map<String, Object> originalParams = extractParameters(joinPoint);

        // 开始审计
        Instant startTime = Instant.now();
        ToolExecutionAudit audit = auditService.startExecution(
                executionId, traceId, sessionId, userId, toolName, methodName, originalParams
        );

        try {
            // 执行原方法
            Object result = joinPoint.proceed();

            // 计算执行时间
            Instant endTime = Instant.now();
            Duration executionTime = Duration.between(startTime, endTime);

            // 处理结果
            if (result instanceof ToolExecutionResult toolResult) {
                // 转换为增强结果
                EnhancedToolExecutionResult enhancedResult = enhanceResult(
                        toolResult, executionId, traceId, sessionId, userId,
                        toolName, methodName, originalParams, executionTime
                );

                // 完成审计
                auditService.completeExecution(
                        executionId,
                        toolResult.status(),
                        toolResult.payload(),
                        null,
                        originalParams,
                        executionTime.toMillis()
                );

                return enhancedResult;
            } else {
                // 非ToolExecutionResult返回值，创建成功结果
                EnhancedToolExecutionResult enhancedResult = EnhancedToolExecutionResult.success(
                        result,
                        "Method executed successfully"
                ).withAuditMetadata(
                        AuditMetadata.create(executionId, traceId, sessionId, userId, toolName, methodName)
                ).withMetrics(
                        PerformanceMetrics.create(executionTime.toMillis())
                );

                auditService.completeExecution(
                        executionId,
                        "ok",
                        result,
                        null,
                        originalParams,
                        executionTime.toMillis()
                );

                return enhancedResult;
            }

        } catch (Exception e) {
            // 处理异常
            Instant endTime = Instant.now();
            Duration executionTime = Duration.between(startTime, endTime);

            auditService.completeExecution(
                    executionId,
                    "error",
                    null,
                    e.getMessage(),
                    originalParams,
                    executionTime.toMillis()
            );

            // 返回错误结果
            EnhancedToolExecutionResult errorResult = EnhancedToolExecutionResult.error(
                    "Tool execution failed: " + e.getMessage()
            ).withAuditMetadata(
                    AuditMetadata.create(executionId, traceId, sessionId, userId, toolName, methodName)
            ).withMetrics(
                    PerformanceMetrics.create(executionTime.toMillis())
            );

            return errorResult;
        }
    }

    private EnhancedToolExecutionResult enhanceResult(ToolExecutionResult original,
                                                      String executionId, String traceId,
                                                      String sessionId, String userId,
                                                      String toolName, String methodName,
                                                      Map<String, Object> originalParams,
                                                      Duration executionTime) {

        // 创建审计元数据
        AuditMetadata auditMetadata = AuditMetadata.create(
                executionId, traceId, sessionId, userId, toolName, methodName
        );

        // 创建参数链（简化实现）
        ParameterChain parameterChain = ParameterChain.create(executionId, originalParams);

        // 创建性能指标
        PerformanceMetrics metrics = PerformanceMetrics.create(executionTime.toMillis());

        // 记录性能指标
        performanceMonitor.recordExecutionMetrics(toolName, methodName, metrics);

        // 创建决策上下文（如果适用）
        DecisionContext decisionContext = null;
        if (original.isAmbiguous() || original.needsConfirmation()) {
            decisionContext = DecisionContext.create(
                    Map.of("toolName", toolName, "methodName", methodName), original.status(), 0.5
            );
            decisionContextManager.saveDecisionContext(sessionId, decisionContext);
        }

        return EnhancedToolExecutionResult.fromLegacy(original)
                .withAuditMetadata(auditMetadata)
                .withParameterChain(parameterChain)
                .withDecisionContext(decisionContext)
                .withMetrics(metrics);
    }

    private Map<String, Object> extractParameters(ProceedingJoinPoint joinPoint) {
        // 简化的参数提取实现
        Object[] args = joinPoint.getArgs();
        Map<String, Object> params = new java.util.HashMap<>();

        for (int i = 0; i < args.length; i++) {
            params.put("arg" + i, args[i]);
        }

        return params;
    }

    private String getTraceId() {
        // 简化实现，实际应该从分布式追踪系统获取
        return UUID.randomUUID().toString();
    }

    private String getSessionId() {
        // 简化实现，实际应该从会话上下文获取
        return "session-" + System.currentTimeMillis();
    }

    private String getUserId() {
        // 简化实现，实际应该从安全上下文获取
        return "user-" + System.currentTimeMillis();
    }
}