package org.zerolg.aidemo2.audit.service;

import org.zerolg.aidemo2.audit.model.DecisionContext;
import org.zerolg.aidemo2.audit.model.DecisionQuery;
import org.zerolg.aidemo2.audit.model.DecisionRequest;
import org.zerolg.aidemo2.audit.model.DecisionSuggestion;

import java.util.List;

/**
 * 决策上下文管理器接口
 */
public interface DecisionContextManager {

    /**
     * 保存决策上下文
     */
    void saveDecisionContext(String sessionId, DecisionContext context);

    /**
     * 获取相关决策历史
     */
    List<DecisionContext> getRelatedDecisions(DecisionQuery query);

    /**
     * 建议一致性决策
     */
    DecisionSuggestion suggestConsistentDecision(DecisionRequest request);

    /**
     * 查找相似的决策上下文
     */
    List<DecisionContext> findSimilarDecisions(String sessionId, String toolName,
                                               java.util.Map<String, Object> parameters,
                                               double similarityThreshold);

    /**
     * 更新决策结果
     */
    void updateDecisionOutcome(String sessionId, String toolName, String decision, boolean successful);

    /**
     * 获取决策成功率统计
     */
    java.util.Map<String, Double> getDecisionSuccessRates(String toolName);
}