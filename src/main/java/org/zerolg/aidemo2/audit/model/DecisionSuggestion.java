package org.zerolg.aidemo2.audit.model;

import java.util.List;
import java.util.Map;

/**
 * 决策建议
 */
public record DecisionSuggestion(
        String suggestedDecision,           // 建议的决策
        double confidence,                  // 置信度
        List<String> alternatives,          // 备选方案
        Map<String, Object> reasoning,      // 推理过程
        List<DecisionContext> similarCases, // 相似案例
        String explanation                  // 解释
) {
    public static DecisionSuggestion create(String suggestedDecision, double confidence, String explanation) {
        return new DecisionSuggestion(
                suggestedDecision,
                confidence,
                List.of(),
                Map.of(),
                List.of(),
                explanation
        );
    }

    public DecisionSuggestion withAlternatives(List<String> alternatives) {
        return new DecisionSuggestion(
                suggestedDecision,
                confidence,
                alternatives,
                reasoning,
                similarCases,
                explanation
        );
    }

    public DecisionSuggestion withReasoning(Map<String, Object> reasoning) {
        return new DecisionSuggestion(
                suggestedDecision,
                confidence,
                alternatives,
                reasoning,
                similarCases,
                explanation
        );
    }

    public DecisionSuggestion withSimilarCases(List<DecisionContext> similarCases) {
        return new DecisionSuggestion(
                suggestedDecision,
                confidence,
                alternatives,
                reasoning,
                similarCases,
                explanation
        );
    }
}