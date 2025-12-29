package org.zerolg.aidemo2.audit.service;

import org.zerolg.aidemo2.audit.model.AuditQuery;
import org.zerolg.aidemo2.audit.model.SessionAuditSummary;
import org.zerolg.aidemo2.audit.model.ToolExecutionAudit;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 审计服务接口
 */
public interface AuditService {

    /**
     * 记录工具调用审计日志
     */
    CompletableFuture<Void> recordToolExecution(ToolExecutionAudit audit);

    /**
     * 查询审计轨迹
     */
    List<ToolExecutionAudit> queryAuditTrail(AuditQuery query);

    /**
     * 获取会话审计摘要
     */
    SessionAuditSummary getSessionSummary(String sessionId);

    /**
     * 开始工具执行审计
     */
    ToolExecutionAudit startExecution(String executionId, String traceId, String sessionId,
                                      String userId, String toolName, String methodName,
                                      java.util.Map<String, Object> originalParams);

    /**
     * 完成工具执行审计
     */
    CompletableFuture<Void> completeExecution(String executionId, String status, Object result,
                                              String errorMessage, java.util.Map<String, Object> finalParams,
                                              long executionTimeMs);

    /**
     * 更新审计记录的参数链
     */
    CompletableFuture<Void> updateParameterChain(String executionId,
                                                 org.zerolg.aidemo2.audit.model.ParameterChain parameterChain);

    /**
     * 更新审计记录的决策上下文
     */
    CompletableFuture<Void> updateDecisionContext(String executionId,
                                                  org.zerolg.aidemo2.audit.model.DecisionContext decisionContext);

    /**
     * 更新审计记录的性能指标
     */
    CompletableFuture<Void> updateMetrics(String executionId,
                                          org.zerolg.aidemo2.audit.model.PerformanceMetrics metrics);
}