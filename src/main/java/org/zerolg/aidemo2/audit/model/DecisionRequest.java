package org.zerolg.aidemo2.audit.model;

import java.util.Map;

/**
 * 决策请求
 */
public record DecisionRequest(
        String sessionId,               // 会话ID
        String toolName,                // 工具名称
        Map<String, Object> parameters, // 当前参数
        Map<String, Object> context     // 上下文信息
) {
    public static DecisionRequest create(String sessionId, String toolName, Map<String, Object> parameters) {
        return new DecisionRequest(sessionId, toolName, parameters, Map.of());
    }

    public DecisionRequest withContext(Map<String, Object> context) {
        return new DecisionRequest(sessionId, toolName, parameters, context);
    }
}