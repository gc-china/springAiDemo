package org.zerolg.aidemo2.audit.service.impl;

import org.zerolg.aidemo2.audit.model.*;
import org.zerolg.aidemo2.audit.service.DecisionContextManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 默认决策上下文管理器实现
 */
@Service
@ConditionalOnProperty(name = "audit.enabled", havingValue = "true", matchIfMissing = false)
public class DefaultDecisionContextManager implements DecisionContextManager {

    private static final Logger logger = LoggerFactory.getLogger(DefaultDecisionContextManager.class);

    // 内存存储，生产环境应该使用数据库
    private final Map<String, List<DecisionContext>> sessionDecisions = new ConcurrentHashMap<>();
    private final Map<String, List<DecisionContext>> toolDecisions = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Integer>> decisionOutcomes = new ConcurrentHashMap<>();

    @Override
    public void saveDecisionContext(String sessionId, DecisionContext context) {
        try {
            // 按会话存储
            sessionDecisions.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(context);

            // 按工具存储 - using parameters to extract tool info
            String toolName = extractToolName(context.parameters());
            if (toolName != null) {
                toolDecisions.computeIfAbsent(toolName, k -> new ArrayList<>()).add(context);
            }

            logger.debug("Saved decision context for session: {} decision: {}", sessionId, context.decision());
        } catch (Exception e) {
            logger.error("Failed to save decision context for session: {}", sessionId, e);
        }
    }

    @Override
    public List<DecisionContext> getRelatedDecisions(DecisionQuery query) {
        List<DecisionContext> allDecisions = new ArrayList<>();

        if (query.sessionId() != null) {
            allDecisions.addAll(sessionDecisions.getOrDefault(query.sessionId(), List.of()));
        } else if (query.toolName() != null) {
            allDecisions.addAll(toolDecisions.getOrDefault(query.toolName(), List.of()));
        } else {
            // 获取所有决策
            sessionDecisions.values().forEach(allDecisions::addAll);
        }

        return allDecisions.stream()
                .filter(decision -> matchesQuery(decision, query))
                .sorted((d1, d2) -> Long.compare(d2.confidence() > d1.confidence() ? 1 : -1, 0)) // Sort by confidence
                .skip(query.offset())
                .limit(query.limit())
                .collect(Collectors.toList());
    }

    @Override
    public DecisionSuggestion suggestConsistentDecision(DecisionRequest request) {
        try {
            // 查找相似的历史决策
            List<DecisionContext> similarDecisions = findSimilarDecisions(
                    request.sessionId(),
                    request.toolName(),
                    request.parameters(),
                    0.7 // 相似度阈值
            );

            if (similarDecisions.isEmpty()) {
                return DecisionSuggestion.create(
                        "no_suggestion",
                        0.0,
                        "No similar historical decisions found"
                );
            }

            // 分析历史决策模式
            Map<String, Long> decisionCounts = similarDecisions.stream()
                    .collect(Collectors.groupingBy(DecisionContext::decision, Collectors.counting()));

            // 找出最常见的决策
            String mostCommonDecision = decisionCounts.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("no_suggestion");

            // 计算置信度
            long totalDecisions = similarDecisions.size();
            long commonDecisionCount = decisionCounts.getOrDefault(mostCommonDecision, 0L);
            double confidence = (double) commonDecisionCount / totalDecisions;

            // 获取备选方案
            List<String> alternatives = decisionCounts.entrySet().stream()
                    .filter(entry -> !entry.getKey().equals(mostCommonDecision))
                    .sorted((e1, e2) -> Long.compare(e2.getValue(), e1.getValue()))
                    .limit(3)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());

            // 构建推理过程
            Map<String, Object> reasoning = Map.of(
                    "similar_cases_count", totalDecisions,
                    "decision_distribution", decisionCounts,
                    "confidence_calculation", String.format("%.2f = %d/%d", confidence, commonDecisionCount, totalDecisions)
            );

            String explanation = String.format(
                    "Based on %d similar cases, '%s' was chosen %d times (%.1f%% confidence)",
                    totalDecisions, mostCommonDecision, commonDecisionCount, confidence * 100
            );

            return new DecisionSuggestion(
                    mostCommonDecision,
                    confidence,
                    alternatives,
                    reasoning,
                    similarDecisions.stream().limit(5).collect(Collectors.toList()),
                    explanation
            );

        } catch (Exception e) {
            logger.error("Failed to suggest consistent decision", e);
            return DecisionSuggestion.create("error", 0.0, "Failed to analyze historical decisions");
        }
    }

    @Override
    public List<DecisionContext> findSimilarDecisions(String sessionId, String toolName,
                                                      Map<String, Object> parameters,
                                                      double similarityThreshold) {
        List<DecisionContext> candidates = new ArrayList<>();

        // 优先从同一会话中查找
        if (sessionId != null) {
            candidates.addAll(sessionDecisions.getOrDefault(sessionId, List.of()));
        }

        // 从同一工具的历史决策中查找
        candidates.addAll(toolDecisions.getOrDefault(toolName, List.of()));

        return candidates.stream()
                .filter(decision -> extractToolName(decision.parameters()) != null &&
                        extractToolName(decision.parameters()).equals(toolName))
                .filter(decision -> calculateSimilarity(decision.parameters(), parameters) >= similarityThreshold)
                .sorted((d1, d2) -> Double.compare(d2.confidence(), d1.confidence()))
                .collect(Collectors.toList());
    }

    @Override
    public void updateDecisionOutcome(String sessionId, String toolName, String decision, boolean successful) {
        try {
            String key = toolName + ":" + decision;
            Map<String, Integer> outcomes = decisionOutcomes.computeIfAbsent(toolName, k -> new ConcurrentHashMap<>());

            if (successful) {
                outcomes.merge(key + ":success", 1, Integer::sum);
            } else {
                outcomes.merge(key + ":failure", 1, Integer::sum);
            }

            logger.debug("Updated decision outcome for tool: {} decision: {} successful: {}",
                    toolName, decision, successful);
        } catch (Exception e) {
            logger.error("Failed to update decision outcome", e);
        }
    }

    @Override
    public Map<String, Double> getDecisionSuccessRates(String toolName) {
        Map<String, Integer> outcomes = decisionOutcomes.getOrDefault(toolName, Map.of());
        Map<String, Double> successRates = new HashMap<>();

        // 按决策分组计算成功率
        Map<String, Map<String, Integer>> decisionGroups = new HashMap<>();

        for (Map.Entry<String, Integer> entry : outcomes.entrySet()) {
            String[] parts = entry.getKey().split(":");
            if (parts.length >= 3) {
                String decision = parts[1];
                String outcome = parts[2];

                decisionGroups.computeIfAbsent(decision, k -> new HashMap<>())
                        .put(outcome, entry.getValue());
            }
        }

        for (Map.Entry<String, Map<String, Integer>> entry : decisionGroups.entrySet()) {
            String decision = entry.getKey();
            Map<String, Integer> counts = entry.getValue();

            int successCount = counts.getOrDefault("success", 0);
            int failureCount = counts.getOrDefault("failure", 0);
            int totalCount = successCount + failureCount;

            if (totalCount > 0) {
                double successRate = (double) successCount / totalCount;
                successRates.put(decision, successRate);
            }
        }

        return successRates;
    }

    private boolean matchesQuery(DecisionContext decision, DecisionQuery query) {
        String toolName = extractToolName(decision.parameters());
        if (query.toolName() != null && !query.toolName().equals(toolName)) {
            return false;
        }
        if (query.minConfidence() > 0 && decision.confidence() < query.minConfidence()) {
            return false;
        }
        // Note: timestamp-based filtering removed as timestamp is not available in simplified model
        if (query.parameters() != null && !parametersMatch(decision.parameters(), query.parameters())) {
            return false;
        }
        return true;
    }

    private String extractToolName(Map<String, Object> parameters) {
        // Extract tool name from parameters if available
        Object toolName = parameters.get("toolName");
        return toolName != null ? toolName.toString() : null;
    }

    private boolean parametersMatch(Map<String, Object> decisionParams, Map<String, Object> queryParams) {
        // 简化的参数匹配逻辑
        for (Map.Entry<String, Object> entry : queryParams.entrySet()) {
            if (!Objects.equals(decisionParams.get(entry.getKey()), entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    private double calculateSimilarity(Map<String, Object> params1, Map<String, Object> params2) {
        if (params1.isEmpty() && params2.isEmpty()) {
            return 1.0;
        }

        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(params1.keySet());
        allKeys.addAll(params2.keySet());

        if (allKeys.isEmpty()) {
            return 1.0;
        }

        int matchingKeys = 0;
        for (String key : allKeys) {
            Object value1 = params1.get(key);
            Object value2 = params2.get(key);

            if (Objects.equals(value1, value2)) {
                matchingKeys++;
            }
        }

        return (double) matchingKeys / allKeys.size();
    }
}