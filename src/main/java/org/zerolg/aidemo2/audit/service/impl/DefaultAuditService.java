package org.zerolg.aidemo2.audit.service.impl;

import org.zerolg.aidemo2.audit.model.*;
import org.zerolg.aidemo2.audit.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 默认审计服务实现
 * 使用内存存储，生产环境应该使用数据库
 */
@Service
@ConditionalOnExpression("'${audit.enabled:false}' == 'true' and '${audit.storage.type:memory}' == 'memory'")
public class DefaultAuditService implements AuditService {

    private static final Logger logger = LoggerFactory.getLogger(DefaultAuditService.class);

    // 内存存储，生产环境应该使用数据库
    private final Map<String, ToolExecutionAudit> auditRecords = new ConcurrentHashMap<>();
    private final Map<String, List<String>> sessionExecutions = new ConcurrentHashMap<>();

    @Override
    @Async
    public CompletableFuture<Void> recordToolExecution(ToolExecutionAudit audit) {
        try {
            auditRecords.put(audit.executionId(), audit);

            // 更新会话执行列表
            sessionExecutions.computeIfAbsent(audit.sessionId(), k -> new ArrayList<>())
                    .add(audit.executionId());

            logger.debug("Recorded audit for execution: {}", audit.executionId());
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            logger.error("Failed to record audit for execution: {}", audit.executionId(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public List<ToolExecutionAudit> queryAuditTrail(AuditQuery query) {
        return auditRecords.values().stream()
                .filter(audit -> matchesQuery(audit, query))
                .sorted((a, b) -> b.startTime().compareTo(a.startTime())) // 最新的在前
                .skip(query.offset())
                .limit(query.limit())
                .collect(Collectors.toList());
    }

    @Override
    public SessionAuditSummary getSessionSummary(String sessionId) {
        List<String> executionIds = sessionExecutions.getOrDefault(sessionId, List.of());
        if (executionIds.isEmpty()) {
            return SessionAuditSummary.empty(sessionId, null);
        }

        List<ToolExecutionAudit> audits = executionIds.stream()
                .map(auditRecords::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (audits.isEmpty()) {
            return SessionAuditSummary.empty(sessionId, null);
        }

        // 计算统计信息
        String userId = audits.get(0).userId();
        Instant startTime = audits.stream().map(ToolExecutionAudit::startTime).min(Instant::compareTo).orElse(Instant.now());
        Instant endTime = audits.stream().map(ToolExecutionAudit::endTime).filter(Objects::nonNull).max(Instant::compareTo).orElse(Instant.now());

        int totalExecutions = audits.size();
        int successfulExecutions = (int) audits.stream().filter(a -> "ok".equals(a.status())).count();
        int failedExecutions = (int) audits.stream().filter(a -> "error".equals(a.status())).count();
        int ambiguousExecutions = (int) audits.stream().filter(a -> "ambiguous".equals(a.status())).count();

        Map<String, Integer> toolUsageCount = audits.stream()
                .collect(Collectors.groupingBy(ToolExecutionAudit::toolName,
                        Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)));

        double averageExecutionTime = audits.stream()
                .mapToLong(ToolExecutionAudit::executionTimeMs)
                .average()
                .orElse(0.0);

        int totalParameterTransformations = audits.stream()
                .map(ToolExecutionAudit::parameterChain)
                .filter(Objects::nonNull)
                .mapToInt(chain -> chain.steps().size())
                .sum();

        double averageConfidence = audits.stream()
                .map(ToolExecutionAudit::parameterChain)
                .filter(Objects::nonNull)
                .mapToDouble(ParameterChain::overallConfidence)
                .average()
                .orElse(1.0);

        return new SessionAuditSummary(
                sessionId, userId, startTime, endTime,
                totalExecutions, successfulExecutions, failedExecutions, ambiguousExecutions,
                toolUsageCount, averageExecutionTime, totalParameterTransformations, averageConfidence
        );
    }

    @Override
    public ToolExecutionAudit startExecution(String executionId, String traceId, String sessionId,
                                             String userId, String toolName, String methodName,
                                             Map<String, Object> originalParams) {
        ToolExecutionAudit audit = ToolExecutionAudit.create(
                executionId, traceId, sessionId, userId, toolName, methodName, originalParams
        );

        auditRecords.put(executionId, audit);
        sessionExecutions.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(executionId);

        logger.debug("Started execution audit: {}", executionId);
        return audit;
    }

    @Override
    @Async
    public CompletableFuture<Void> completeExecution(String executionId, String status, Object result,
                                                     String errorMessage, Map<String, Object> finalParams,
                                                     long executionTimeMs) {
        try {
            ToolExecutionAudit existing = auditRecords.get(executionId);
            if (existing != null) {
                ToolExecutionAudit completed = existing.withCompletion(status, result, errorMessage, finalParams, executionTimeMs);
                auditRecords.put(executionId, completed);
                logger.debug("Completed execution audit: {}", executionId);
            }
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            logger.error("Failed to complete execution audit: {}", executionId, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    @Async
    public CompletableFuture<Void> updateParameterChain(String executionId, ParameterChain parameterChain) {
        try {
            ToolExecutionAudit existing = auditRecords.get(executionId);
            if (existing != null) {
                ToolExecutionAudit updated = existing.withParameterChain(parameterChain);
                auditRecords.put(executionId, updated);
                logger.debug("Updated parameter chain for execution: {}", executionId);
            }
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            logger.error("Failed to update parameter chain for execution: {}", executionId, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    @Async
    public CompletableFuture<Void> updateDecisionContext(String executionId, DecisionContext decisionContext) {
        try {
            ToolExecutionAudit existing = auditRecords.get(executionId);
            if (existing != null) {
                ToolExecutionAudit updated = existing.withDecisionContext(decisionContext);
                auditRecords.put(executionId, updated);
                logger.debug("Updated decision context for execution: {}", executionId);
            }
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            logger.error("Failed to update decision context for execution: {}", executionId, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    @Async
    public CompletableFuture<Void> updateMetrics(String executionId, PerformanceMetrics metrics) {
        try {
            ToolExecutionAudit existing = auditRecords.get(executionId);
            if (existing != null) {
                ToolExecutionAudit updated = existing.withMetrics(metrics);
                auditRecords.put(executionId, updated);
                logger.debug("Updated metrics for execution: {}", executionId);
            }
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            logger.error("Failed to update metrics for execution: {}", executionId, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    private boolean matchesQuery(ToolExecutionAudit audit, AuditQuery query) {
        if (query.sessionId() != null && !query.sessionId().equals(audit.sessionId())) {
            return false;
        }
        if (query.userId() != null && !query.userId().equals(audit.userId())) {
            return false;
        }
        if (query.toolName() != null && !query.toolName().equals(audit.toolName())) {
            return false;
        }
        if (query.statuses() != null && !query.statuses().contains(audit.status())) {
            return false;
        }
        if (query.startTime() != null && audit.startTime().isBefore(query.startTime())) {
            return false;
        }
        if (query.endTime() != null && audit.startTime().isAfter(query.endTime())) {
            return false;
        }
        return true;
    }
}