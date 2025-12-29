package org.zerolg.aidemo2.audit.model;

import java.util.Map;

/**
 * 决策上下文
 */
public record DecisionContext(
        Map<String, Object> parameters,      // 参数
        String decision,                     // 决策结果
        double confidence,                   // 决策置信度
        Map<String, Object> alternatives,    // 备选方案
        Map<String, Object> contextFactors   // 上下文因素
) {
    public static DecisionContext create(Map<String, Object> parameters, String decision,
                                         double confidence) {
        return new DecisionContext(
                parameters,
                decision,
                confidence,
                Map.of(),
                Map.of()
        );
    }

    public DecisionContext withAlternatives(Map<String, Object> alternatives) {
        return new DecisionContext(
                parameters,
                decision,
                confidence,
                alternatives,
                contextFactors
        );
    }

    public DecisionContext withContextFactors(Map<String, Object> contextFactors) {
        return new DecisionContext(
                parameters,
                decision,
                confidence,
                alternatives,
                contextFactors
        );
    }
}