package org.zerolg.aidemo2.common;

import org.zerolg.aidemo2.audit.model.AuditMetadata;
import org.zerolg.aidemo2.audit.model.DecisionContext;
import org.zerolg.aidemo2.audit.model.ParameterChain;
import org.zerolg.aidemo2.audit.model.PerformanceMetrics;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 增强的工具执行结果，包含审计和可追溯信息
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EnhancedToolExecutionResult(
        String status,                    // ok | ambiguous | not_found | error | needs_confirmation
        Object payload,                   // 执行结果或候选数据
        String explain,                   // 自然语言解释
        AuditMetadata auditMetadata,      // 审计元数据
        ParameterChain parameterChain,    // 参数转换链
        DecisionContext decisionContext,  // 决策上下文
        PerformanceMetrics metrics        // 性能指标
) {
    private static final ObjectMapper mapper = new ObjectMapper();

    // 向后兼容的构造方法
    public static EnhancedToolExecutionResult fromLegacy(ToolExecutionResult legacy) {
        return new EnhancedToolExecutionResult(
                legacy.status(),
                legacy.payload(),
                legacy.explain(),
                null, null, null, null
        );
    }

    // 快捷构建方法
    public static EnhancedToolExecutionResult success(Object payload, String explain) {
        return new EnhancedToolExecutionResult("ok", payload, explain, null, null, null, null);
    }

    public static EnhancedToolExecutionResult error(String explain) {
        return new EnhancedToolExecutionResult("error", null, explain, null, null, null, null);
    }

    public static EnhancedToolExecutionResult notFound(String explain) {
        return new EnhancedToolExecutionResult("not_found", null, explain, null, null, null, null);
    }

    public static EnhancedToolExecutionResult ambiguous(Object candidates, String explain) {
        return new EnhancedToolExecutionResult("ambiguous", candidates, explain, null, null, null, null);
    }

    public static EnhancedToolExecutionResult needsConfirmation(Object data, String explain) {
        return new EnhancedToolExecutionResult("needs_confirmation", data, explain, null, null, null, null);
    }

    // 带审计信息的构建方法
    public static EnhancedToolExecutionResult success(Object payload, String explain,
                                                      AuditMetadata auditMetadata,
                                                      ParameterChain parameterChain,
                                                      DecisionContext decisionContext,
                                                      PerformanceMetrics metrics) {
        return new EnhancedToolExecutionResult("ok", payload, explain, auditMetadata,
                parameterChain, decisionContext, metrics);
    }

    // 状态检查方法
    public boolean isSuccess() {
        return "ok".equals(status);
    }

    public boolean isError() {
        return "error".equals(status);
    }

    public boolean isNotFound() {
        return "not_found".equals(status);
    }

    public boolean isAmbiguous() {
        return "ambiguous".equals(status);
    }

    public boolean needsConfirmation() {
        return "needs_confirmation".equals(status);
    }

    /**
     * 获取payload数据
     */
    public Object getPayload() {
        return payload;
    }

    /**
     * 添加审计元数据
     */
    public EnhancedToolExecutionResult withAuditMetadata(AuditMetadata auditMetadata) {
        return new EnhancedToolExecutionResult(status, payload, explain, auditMetadata,
                parameterChain, decisionContext, metrics);
    }

    /**
     * 添加参数链
     */
    public EnhancedToolExecutionResult withParameterChain(ParameterChain parameterChain) {
        return new EnhancedToolExecutionResult(status, payload, explain, auditMetadata,
                parameterChain, decisionContext, metrics);
    }

    /**
     * 添加决策上下文
     */
    public EnhancedToolExecutionResult withDecisionContext(DecisionContext decisionContext) {
        return new EnhancedToolExecutionResult(status, payload, explain, auditMetadata,
                parameterChain, decisionContext, metrics);
    }

    /**
     * 添加性能指标
     */
    public EnhancedToolExecutionResult withMetrics(PerformanceMetrics metrics) {
        return new EnhancedToolExecutionResult(status, payload, explain, auditMetadata,
                parameterChain, decisionContext, metrics);
    }

    /**
     * 转换为简化的ToolExecutionResult（向后兼容）
     */
    public ToolExecutionResult toLegacy() {
        return new ToolExecutionResult(status, payload, explain);
    }

    // 方便转换为 JSON 字符串返回给 LLM
    public String toJson() {
        try {
            return mapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return "{\"status\":\"error\",\"explain\":\"Serialization failed\"}";
        }
    }

    /**
     * 转换为简化的JSON（不包含审计信息，给LLM使用）
     */
    public String toSimpleJson() {
        return toLegacy().toJson();
    }
}