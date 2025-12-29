package org.zerolg.aidemo2.audit.model;

import java.time.Instant;
import java.util.Map;

/**
 * 决策查询条件
 */
public record DecisionQuery(
        String sessionId,               // 会话ID
        String toolName,                // 工具名称
        Map<String, Object> parameters, // 参数模式
        Instant startTime,              // 开始时间
        Instant endTime,                // 结束时间
        double minConfidence,           // 最小置信度
        int limit,                      // 限制数量
        int offset                      // 偏移量
) {
    public static DecisionQuery forSession(String sessionId) {
        return new DecisionQuery(sessionId, null, null, null, null, 0.0, 100, 0);
    }

    public static DecisionQuery forTool(String toolName) {
        return new DecisionQuery(null, toolName, null, null, null, 0.0, 100, 0);
    }

    public static DecisionQuery forParameters(Map<String, Object> parameters) {
        return new DecisionQuery(null, null, parameters, null, null, 0.0, 100, 0);
    }

    public DecisionQuery withMinConfidence(double minConfidence) {
        return new DecisionQuery(sessionId, toolName, parameters, startTime, endTime, minConfidence, limit, offset);
    }

    public DecisionQuery withLimit(int limit) {
        return new DecisionQuery(sessionId, toolName, parameters, startTime, endTime, minConfidence, limit, offset);
    }
}