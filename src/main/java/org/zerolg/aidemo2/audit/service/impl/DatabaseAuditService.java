package org.zerolg.aidemo2.audit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zerolg.aidemo2.audit.entity.*;
import org.zerolg.aidemo2.audit.mapper.*;
import org.zerolg.aidemo2.audit.model.*;
import org.zerolg.aidemo2.audit.service.AuditService;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 基于PostgreSQL数据库的审计服务实现
 * 提供完整的数据持久化和查询能力
 */
@Service
@ConditionalOnExpression("'${audit.enabled:false}' == 'true' and '${audit.storage.type:database}' == 'database'")
public class DatabaseAuditService implements AuditService {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseAuditService.class);

    @Autowired
    private ToolExecutionAuditMapper auditMapper;

    @Autowired
    private ParameterChainMapper parameterChainMapper;

    @Autowired
    private DecisionContextMapper decisionContextMapper;

    @Autowired
    private PerformanceMetricsMapper performanceMetricsMapper;

    @Autowired(required = false)
    private ParameterChainRecorder parameterChainRecorder;

    @Override
    @Async
    @Transactional
    public CompletableFuture<Void> recordToolExecution(ToolExecutionAudit audit) {
        try {
            // 1. 保存主审计记录
            ToolExecutionAuditEntity auditEntity = convertToEntity(audit);
            auditMapper.insert(auditEntity);

            // 2. 保存参数转换链
            if (audit.parameterChain() != null && !audit.parameterChain().steps().isEmpty()) {
                saveParameterChain(audit.executionId(), audit.parameterChain());
            }

            // 3. 保存决策上下文
            if (audit.decisionContext() != null) {
                String toolName = extractToolNameFromParams(audit.originalParams());
                saveDecisionContext(audit.executionId(), audit.sessionId(), toolName, audit.decisionContext());
            }

            // 4. 保存性能指标
            if (audit.metrics() != null) {
                savePerformanceMetrics(audit.executionId(), audit.metrics());
            }

            logger.debug("Recorded audit to database: {}", audit.executionId());
            return CompletableFuture.completedFuture(null);

        } catch (Exception e) {
            logger.error("Failed to record audit to database: {}", audit.executionId(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public List<ToolExecutionAudit> queryAuditTrail(AuditQuery query) {
        try {
            logger.info("查询审计轨迹: startTime={}, endTime={}, offset={}, limit={}",
                    query.startTime(), query.endTime(), query.offset(), query.limit());
            
            QueryWrapper<ToolExecutionAuditEntity> queryWrapper = new QueryWrapper<>();

            // 构建查询条件
            if (query.sessionId() != null) {
                queryWrapper.eq("session_id", query.sessionId());
            }
            if (query.userId() != null) {
                queryWrapper.eq("user_id", query.userId());
            }
            if (query.toolName() != null) {
                queryWrapper.eq("tool_name", query.toolName());
            }
            if (query.statuses() != null && !query.statuses().isEmpty()) {
                queryWrapper.in("status", query.statuses());
            }
            // 注意：不再使用时间过滤，直接查询所有记录
            // 因为时区问题可能导致查询不到数据

            // 排序和分页
            queryWrapper.orderByDesc("start_time");
            queryWrapper.last("LIMIT " + query.limit() + " OFFSET " + query.offset());

            List<ToolExecutionAuditEntity> entities = auditMapper.selectList(queryWrapper);
            logger.info("查询到 {} 条审计记录", entities.size());

            // 转换为审计对象并加载关联数据
            return entities.stream()
                    .map(this::convertToAudit)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            logger.error("Failed to query audit trail from database", e);
            return List.of();
        }
    }

    @Override
    public SessionAuditSummary getSessionSummary(String sessionId) {
        try {
            Map<String, Object> stats = auditMapper.getSessionStatistics(sessionId);

            if (stats == null || stats.isEmpty()) {
                return SessionAuditSummary.empty(sessionId, null);
            }

            // 获取会话的基本信息
            QueryWrapper<ToolExecutionAuditEntity> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("session_id", sessionId);
            queryWrapper.orderByAsc("start_time");
            queryWrapper.last("LIMIT 1");

            ToolExecutionAuditEntity firstExecution = auditMapper.selectOne(queryWrapper);
            if (firstExecution == null) {
                return SessionAuditSummary.empty(sessionId, null);
            }

            // 获取时间范围
            queryWrapper.clear();
            queryWrapper.eq("session_id", sessionId);
            queryWrapper.orderByDesc("end_time");
            queryWrapper.last("LIMIT 1");

            ToolExecutionAuditEntity lastExecution = auditMapper.selectOne(queryWrapper);

            // 获取工具使用统计
            List<Map<String, Object>> toolUsage = auditMapper.getToolUsageStatistics(
                    firstExecution.getStartTime()
            );

            Map<String, Integer> toolUsageCount = toolUsage.stream()
                    .collect(Collectors.toMap(
                            m -> (String) m.get("tool_name"),
                            m -> ((Number) m.get("usage_count")).intValue()
                    ));

            // 构建摘要
            return new SessionAuditSummary(
                    sessionId,
                    firstExecution.getUserId(),
                    firstExecution.getStartTime(),
                    lastExecution != null && lastExecution.getEndTime() != null ?
                            lastExecution.getEndTime() : Instant.now(),
                    ((Number) stats.get("total_executions")).intValue(),
                    ((Number) stats.get("successful_executions")).intValue(),
                    ((Number) stats.get("failed_executions")).intValue(),
                    ((Number) stats.get("ambiguous_executions")).intValue(),
                    toolUsageCount,
                    ((Number) stats.get("average_execution_time")).doubleValue(),
                    0, // TODO: 计算参数转换总数
                    1.0 // TODO: 计算平均置信度
            );

        } catch (Exception e) {
            logger.error("Failed to get session summary from database: {}", sessionId, e);
            return SessionAuditSummary.empty(sessionId, null);
        }
    }

    @Override
    @Transactional
    public ToolExecutionAudit startExecution(String executionId, String traceId, String sessionId,
                                             String userId, String toolName, String methodName,
                                             Map<String, Object> originalParams) {
        ToolExecutionAudit audit = ToolExecutionAudit.create(
                executionId, traceId, sessionId, userId, toolName, methodName, originalParams
        );

        // 立即保存到数据库
        try {
            ToolExecutionAuditEntity auditEntity = new ToolExecutionAuditEntity();
            auditEntity.setExecutionId(executionId);
            auditEntity.setTraceId(traceId);
            auditEntity.setSessionId(sessionId);
            auditEntity.setUserId(userId);
            auditEntity.setToolName(toolName);
            auditEntity.setMethodName(methodName);
            auditEntity.setOriginalParams(originalParams);
            auditEntity.setStatus("running");
            auditEntity.setStartTime(Instant.now());
            
            auditMapper.insert(auditEntity);
            logger.info("审计记录创建成功: executionId={}, toolName={}, methodName={}",
                    executionId, toolName, methodName);
        } catch (Exception e) {
            logger.error("Failed to start execution audit in database: {}", executionId, e);
        }

        return audit;
    }

    @Override
    @Transactional
    public CompletableFuture<Void> completeExecution(String executionId, String status, Object result,
                                                     String errorMessage, Map<String, Object> finalParams,
                                                     long executionTimeMs) {
        logger.info("开始完成审计: executionId={}, status={}", executionId, status);
        try {
            QueryWrapper<ToolExecutionAuditEntity> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("execution_id", executionId);

            ToolExecutionAuditEntity existing = auditMapper.selectOne(queryWrapper);
            if (existing != null) {
                existing.setStatus(status);
                existing.setResult(result);
                existing.setErrorMessage(errorMessage);
                existing.setFinalParams(finalParams);
                existing.setExecutionTimeMs(executionTimeMs);
                existing.setEndTime(Instant.now());

                int rows = auditMapper.updateById(existing);
                logger.info("审计记录更新完成: executionId={}, status={}, executionTimeMs={}, 更新行数={}",
                        executionId, status, executionTimeMs, rows);

                // 检查是否有参数链数据需要关联
                QueryWrapper<ParameterChainEntity> chainQuery = new QueryWrapper<>();
                chainQuery.eq("execution_id", executionId);
                List<ParameterChainEntity> chainEntities = parameterChainMapper.selectList(chainQuery);
                if (!chainEntities.isEmpty()) {
                    logger.info("找到 {} 个参数转换步骤: executionId={}", chainEntities.size(), executionId);
                }
            } else {
                logger.warn("未找到审计记录，无法更新: executionId={}", executionId);
            }

            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            logger.error("完成审计失败: executionId={}", executionId, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    @Async
    @Transactional
    public CompletableFuture<Void> updateParameterChain(String executionId, ParameterChain parameterChain) {
        try {
            // 删除现有的参数链记录
            parameterChainMapper.deleteByExecutionId(executionId);

            // 保存新的参数链
            saveParameterChain(executionId, parameterChain);

            logger.debug("Updated parameter chain in database: {}", executionId);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            logger.error("Failed to update parameter chain in database: {}", executionId, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    @Async
    @Transactional
    public CompletableFuture<Void> updateDecisionContext(String executionId, DecisionContext decisionContext) {
        try {
            // 查找现有记录
            DecisionContextEntity existing = decisionContextMapper.selectByExecutionId(executionId);

            if (existing != null) {
                // 更新现有记录
                updateDecisionContextEntity(existing, decisionContext);
                decisionContextMapper.updateById(existing);
            } else {
                // 创建新记录（需要从审计记录获取会话ID和工具名称）
                QueryWrapper<ToolExecutionAuditEntity> queryWrapper = new QueryWrapper<>();
                queryWrapper.eq("execution_id", executionId);
                ToolExecutionAuditEntity auditEntity = auditMapper.selectOne(queryWrapper);

                if (auditEntity != null) {
                    saveDecisionContext(executionId, auditEntity.getSessionId(), auditEntity.getToolName(), decisionContext);
                }
            }

            logger.debug("Updated decision context in database: {}", executionId);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            logger.error("Failed to update decision context in database: {}", executionId, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    @Async
    @Transactional
    public CompletableFuture<Void> updateMetrics(String executionId, PerformanceMetrics metrics) {
        try {
            // 查找现有记录
            PerformanceMetricsEntity existing = performanceMetricsMapper.selectByExecutionId(executionId);

            if (existing != null) {
                // 更新现有记录
                updatePerformanceMetricsEntity(existing, metrics);
                performanceMetricsMapper.updateById(existing);
            } else {
                // 创建新记录
                savePerformanceMetrics(executionId, metrics);
            }

            logger.debug("Updated metrics in database: {}", executionId);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            logger.error("Failed to update metrics in database: {}", executionId, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    // ========================================================================
    // 辅助方法 - 实体转换
    // ========================================================================

    private ToolExecutionAuditEntity convertToEntity(ToolExecutionAudit audit) {
        ToolExecutionAuditEntity entity = new ToolExecutionAuditEntity();
        entity.setExecutionId(audit.executionId());
        entity.setTraceId(audit.traceId());
        entity.setSessionId(audit.sessionId());
        entity.setUserId(audit.userId());
        entity.setToolName(extractToolNameFromParams(audit.originalParams()));
        entity.setMethodName(audit.methodName());
        entity.setOriginalParams(audit.originalParams());
        entity.setFinalParams(audit.finalParams());
        entity.setStatus(audit.status());
        entity.setResult(audit.result());
        entity.setErrorMessage(audit.errorMessage());
        entity.setStartTime(audit.startTime());
        entity.setEndTime(audit.endTime());
        entity.setExecutionTimeMs(audit.executionTimeMs());

        // 构建上下文信息
        Map<String, Object> context = new HashMap<>();
        if (audit.parameterChain() != null) {
            context.put("parameterChainSteps", audit.parameterChain().steps().size());
            context.put("overallConfidence", audit.parameterChain().overallConfidence());
        }
        if (audit.decisionContext() != null) {
            context.put("hasDecisionContext", true);
            context.put("decisionConfidence", audit.decisionContext().confidence());
        }
        if (audit.metrics() != null) {
            context.put("hasMetrics", true);
            context.put("cacheHit", audit.metrics().cacheHit());
        }
        entity.setContext(context);

        return entity;
    }

    private ToolExecutionAudit convertToAudit(ToolExecutionAuditEntity entity) {
        // 加载关联数据
        ParameterChain parameterChain = loadParameterChain(entity.getExecutionId());
        DecisionContext decisionContext = loadDecisionContext(entity.getExecutionId());
        PerformanceMetrics metrics = loadPerformanceMetrics(entity.getExecutionId());

        return new ToolExecutionAudit(
                entity.getId() != null ? entity.getId().toString() : java.util.UUID.randomUUID().toString(),
                entity.getExecutionId(),
                entity.getTraceId(),
                entity.getSessionId(),
                entity.getUserId(),
                entity.getToolName(),
                entity.getMethodName(),
                entity.getOriginalParams(),
                entity.getFinalParams(),
                entity.getStatus(),
                entity.getResult(),
                entity.getErrorMessage(),
                entity.getStartTime(),
                entity.getEndTime(),
                entity.getExecutionTimeMs() != null ? entity.getExecutionTimeMs() : 0L,
                entity.getContext() != null ? entity.getContext() : Map.of(),
                parameterChain,
                decisionContext,
                metrics
        );
    }

    private void saveParameterChain(String executionId, ParameterChain parameterChain) {
        if (parameterChain == null || parameterChain.steps().isEmpty()) {
            return;
        }

        int stepOrder = 0;
        for (ParameterTransformation step : parameterChain.steps()) {
            ParameterChainEntity entity = new ParameterChainEntity();
            entity.setExecutionId(executionId);
            entity.setParameterName(step.parameterName());
            entity.setOriginalValue(step.originalValue() != null ? step.originalValue().toString() : null);
            entity.setTransformedValue(step.transformedValue() != null ? step.transformedValue().toString() : null);
            entity.setTransformationType(step.transformationType());
            entity.setConfidence(BigDecimal.valueOf(step.confidence()));
            entity.setReason(step.reason());
            entity.setMetadata(step.metadata());
            entity.setStepOrder(stepOrder++);

            parameterChainMapper.insert(entity);
        }
    }

    private void saveDecisionContext(String executionId, String sessionId, String toolName, DecisionContext decisionContext) {
        DecisionContextEntity entity = new DecisionContextEntity();
        entity.setExecutionId(executionId);
        entity.setSessionId(sessionId);
        entity.setToolName(toolName);
        entity.setParameters(decisionContext.parameters());
        entity.setDecision(decisionContext.decision());
        entity.setConfidence(BigDecimal.valueOf(decisionContext.confidence()));
        entity.setAlternatives(decisionContext.alternatives());
        entity.setContextFactors(decisionContext.contextFactors());

        decisionContextMapper.insert(entity);
    }

    private void savePerformanceMetrics(String executionId, PerformanceMetrics metrics) {
        PerformanceMetricsEntity entity = new PerformanceMetricsEntity();
        entity.setExecutionId(executionId);
        entity.setExecutionTimeMs(metrics.executionTimeMs());
        entity.setParameterCorrectionTimeMs(metrics.parameterCorrectionTimeMs());
        entity.setParameterTransformations(metrics.parameterTransformations());
        entity.setCacheHit(metrics.cacheHit());
        entity.setCustomMetrics(metrics.customMetrics());

        performanceMetricsMapper.insert(entity);
    }

    private ParameterChain loadParameterChain(String executionId) {
        // 首先尝试从内存中的ParameterChainRecorder获取
        if (parameterChainRecorder != null) {
            try {
                ParameterChain memoryChain = parameterChainRecorder.getParameterChain(executionId);

                if (memoryChain != null) {
                    logger.debug("从内存中加载参数链: executionId={}, 步骤数={}", executionId, memoryChain.steps().size());

                    // 同时保存到数据库以便持久化
                    if (!memoryChain.steps().isEmpty()) {
                        try {
                            saveParameterChain(executionId, memoryChain);
                        } catch (Exception e) {
                            logger.warn("保存参数链到数据库失败: executionId={}", executionId, e);
                        }
                    }

                    return memoryChain;
                }
            } catch (Exception e) {
                logger.warn("从内存加载参数链失败，尝试从数据库加载: executionId={}", executionId, e);
            }
        }

        // 从数据库加载
        List<ParameterChainEntity> entities = parameterChainMapper.selectByExecutionId(executionId);
        if (entities.isEmpty()) {
            logger.debug("未找到参数链: executionId={}", executionId);
            return null;
        }

        List<ParameterTransformation> steps = entities.stream()
                .sorted(Comparator.comparing(ParameterChainEntity::getStepOrder))
                .map(entity -> ParameterTransformation.create(
                        entity.getParameterName(),
                        entity.getOriginalValue(),
                        entity.getTransformedValue(),
                        entity.getTransformationType(),
                        entity.getConfidence().doubleValue(),
                        entity.getReason()
                ).withMetadata(entity.getMetadata()))
                .collect(Collectors.toList());

        logger.debug("从数据库加载参数链: executionId={}, 步骤数={}", executionId, steps.size());
        return ParameterChain.create(executionId, Map.of()).withSteps(steps);
    }

    private DecisionContext loadDecisionContext(String executionId) {
        DecisionContextEntity entity = decisionContextMapper.selectByExecutionId(executionId);
        if (entity == null) {
            return null;
        }

        return new DecisionContext(
                entity.getParameters(),
                entity.getDecision(),
                entity.getConfidence().doubleValue(),
                entity.getAlternatives(),
                entity.getContextFactors()
        );
    }

    private PerformanceMetrics loadPerformanceMetrics(String executionId) {
        PerformanceMetricsEntity entity = performanceMetricsMapper.selectByExecutionId(executionId);
        if (entity == null) {
            return null;
        }

        return new PerformanceMetrics(
                entity.getExecutionTimeMs(),
                entity.getParameterCorrectionTimeMs() != null ? entity.getParameterCorrectionTimeMs() : 0L,
                entity.getParameterTransformations() != null ? entity.getParameterTransformations() : 0,
                entity.getCacheHit() != null ? entity.getCacheHit() : false,
                entity.getCustomMetrics() != null ? entity.getCustomMetrics() : Map.of()
        );
    }

    private void updateDecisionContextEntity(DecisionContextEntity entity, DecisionContext decisionContext) {
        entity.setParameters(decisionContext.parameters());
        entity.setDecision(decisionContext.decision());
        entity.setConfidence(BigDecimal.valueOf(decisionContext.confidence()));
        entity.setAlternatives(decisionContext.alternatives());
        entity.setContextFactors(decisionContext.contextFactors());
    }

    private void updatePerformanceMetricsEntity(PerformanceMetricsEntity entity, PerformanceMetrics metrics) {
        entity.setExecutionTimeMs(metrics.executionTimeMs());
        entity.setParameterCorrectionTimeMs(metrics.parameterCorrectionTimeMs());
        entity.setParameterTransformations(metrics.parameterTransformations());
        entity.setCacheHit(metrics.cacheHit());
        entity.setCustomMetrics(metrics.customMetrics());
    }

    // ========================================================================
    // 辅助方法
    // ========================================================================

    private String extractToolNameFromParams(Map<String, Object> params) {
        if (params == null) return "UnknownTool";
        Object toolName = params.get("toolName");
        return toolName != null ? toolName.toString() : "UnknownTool";
    }
}