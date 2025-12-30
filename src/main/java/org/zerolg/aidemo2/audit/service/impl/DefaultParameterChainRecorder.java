package org.zerolg.aidemo2.audit.service.impl;

import org.zerolg.aidemo2.audit.model.*;
import org.zerolg.aidemo2.audit.service.ParameterChainRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 默认参数链记录器实现
 */
@Service
@ConditionalOnProperty(name = "audit.enabled", havingValue = "true", matchIfMissing = false)
public class DefaultParameterChainRecorder implements ParameterChainRecorder {

    private static final Logger logger = LoggerFactory.getLogger(DefaultParameterChainRecorder.class);

    // 内存存储，生产环境应该使用数据库
    private final Map<String, ParameterChain> parameterChains = new ConcurrentHashMap<>();
    private final Map<String, List<String>> toolChains = new ConcurrentHashMap<>();

    @Override
    public void recordParameterChain(String executionId, ParameterChain chain) {
        try {
            parameterChains.put(executionId, chain);

            // 按工具分类存储
            String toolName = extractToolNameFromExecutionId(executionId);
            toolChains.computeIfAbsent(toolName, k -> new ArrayList<>()).add(executionId);

            logger.debug("Recorded parameter chain for execution: {}", executionId);
        } catch (Exception e) {
            logger.error("Failed to record parameter chain for execution: {}", executionId, e);
        }
    }

    @Override
    public List<ParameterChain> queryParameterHistory(ParameterQuery query) {
        return parameterChains.values().stream()
                .filter(chain -> matchesQuery(chain, query))
                .skip(query.offset())
                .limit(query.limit())
                .collect(Collectors.toList());
    }

    @Override
    public ParameterPatternAnalysis analyzePatterns(String toolName, Duration timeRange) {
        List<String> executionIds = toolChains.getOrDefault(toolName, List.of());
        if (executionIds.isEmpty()) {
            return ParameterPatternAnalysis.empty(toolName);
        }

        Instant cutoffTime = Instant.now().minus(timeRange);
        List<ParameterChain> chains = executionIds.stream()
                .map(parameterChains::get)
                .filter(Objects::nonNull)
                .filter(chain -> isWithinTimeRange(chain, cutoffTime))
                .collect(Collectors.toList());

        if (chains.isEmpty()) {
            return ParameterPatternAnalysis.empty(toolName);
        }

        // 分析转换类型
        Map<String, Integer> transformationTypes = new HashMap<>();
        Map<String, List<Double>> confidenceByType = new HashMap<>();
        Map<String, Integer> parameterFrequency = new HashMap<>();

        int totalTransformations = 0;
        for (ParameterChain chain : chains) {
            for (ParameterTransformation transformation : chain.steps()) {
                totalTransformations++;

                // 统计转换类型
                transformationTypes.merge(transformation.transformationType(), 1, Integer::sum);

                // 统计置信度
                confidenceByType.computeIfAbsent(transformation.transformationType(), k -> new ArrayList<>())
                        .add(transformation.confidence());

                // 统计参数频率
                parameterFrequency.merge(transformation.parameterName(), 1, Integer::sum);
            }
        }

        // 计算平均置信度
        Map<String, Double> averageConfidence = confidenceByType.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0.0)
                ));

        // 计算成功率（基于置信度）
        double overallSuccessRate = chains.stream()
                .mapToDouble(ParameterChain::overallConfidence)
                .average()
                .orElse(0.0);

        // 识别常见模式
        Map<String, String> commonPatterns = identifyCommonPatterns(chains);

        return new ParameterPatternAnalysis(
                toolName,
                totalTransformations,
                transformationTypes,
                averageConfidence,
                parameterFrequency,
                overallSuccessRate,
                commonPatterns
        );
    }

    @Override
    public Map<String, Object> getTransformationStats(String toolName) {
        List<String> executionIds = toolChains.getOrDefault(toolName, List.of());
        List<ParameterChain> chains = executionIds.stream()
                .map(parameterChains::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalChains", chains.size());
        stats.put("totalTransformations", chains.stream().mapToInt(c -> c.steps().size()).sum());
        stats.put("averageConfidence", chains.stream().mapToDouble(ParameterChain::overallConfidence).average().orElse(0.0));
        stats.put("averageTransformationsPerChain", chains.stream().mapToInt(c -> c.steps().size()).average().orElse(0.0));

        return stats;
    }

    @Override
    public List<ParameterTransformation> getMostCommonTransformations(String toolName, int limit) {
        List<String> executionIds = toolChains.getOrDefault(toolName, List.of());

        Map<String, List<ParameterTransformation>> transformationsByType = new HashMap<>();

        for (String executionId : executionIds) {
            ParameterChain chain = parameterChains.get(executionId);
            if (chain != null) {
                for (ParameterTransformation transformation : chain.steps()) {
                    String key = transformation.transformationType() + ":" + transformation.parameterName();
                    transformationsByType.computeIfAbsent(key, k -> new ArrayList<>()).add(transformation);
                }
            }
        }

        return transformationsByType.entrySet().stream()
                .sorted((e1, e2) -> Integer.compare(e2.getValue().size(), e1.getValue().size()))
                .limit(limit)
                .map(entry -> entry.getValue().get(0)) // 取第一个作为代表
                .collect(Collectors.toList());
    }

    @Override
    public ParameterChain getParameterChain(String executionId) {
        return parameterChains.get(executionId);
    }

    private boolean matchesQuery(ParameterChain chain, ParameterQuery query) {
        // 简化的查询匹配逻辑
        if (query.parameterName() != null) {
            boolean hasParameter = chain.steps().stream()
                    .anyMatch(step -> query.parameterName().equals(step.parameterName()));
            if (!hasParameter) return false;
        }

        if (query.transformationType() != null) {
            boolean hasType = chain.steps().stream()
                    .anyMatch(step -> query.transformationType().equals(step.transformationType()));
            if (!hasType) return false;
        }

        return true;
    }

    private boolean isWithinTimeRange(ParameterChain chain, Instant cutoffTime) {
        // 简化实现，实际应该基于时间戳
        return true;
    }

    private String extractToolNameFromExecutionId(String executionId) {
        // 简化实现，从executionId中提取工具名称
        // 实际实现应该基于更复杂的逻辑或从其他地方获取
        return "unknown";
    }

    private Map<String, String> identifyCommonPatterns(List<ParameterChain> chains) {
        Map<String, String> patterns = new HashMap<>();

        // 识别最常见的转换序列
        Map<String, Integer> sequenceCount = new HashMap<>();
        for (ParameterChain chain : chains) {
            String sequence = chain.steps().stream()
                    .map(ParameterTransformation::transformationType)
                    .collect(Collectors.joining("->"));
            sequenceCount.merge(sequence, 1, Integer::sum);
        }

        // 找出最常见的模式
        sequenceCount.entrySet().stream()
                .sorted((e1, e2) -> Integer.compare(e2.getValue(), e1.getValue()))
                .limit(5)
                .forEach(entry -> patterns.put("sequence_" + patterns.size(), entry.getKey()));

        return patterns;
    }
}