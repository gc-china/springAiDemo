package org.zerolg.aidemo2.correction.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.zerolg.aidemo2.correction.AmbiguityHandler;
import org.zerolg.aidemo2.correction.model.CorrectionResult;
import org.zerolg.aidemo2.correction.model.ParameterContext;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 默认歧义处理器
 * 提供基础的多候选处理策略
 */
@Component
public class DefaultAmbiguityHandler implements AmbiguityHandler {

    private static final Logger logger = LoggerFactory.getLogger(DefaultAmbiguityHandler.class);

    // 最大候选数量
    private static final int MAX_CANDIDATES = 5;

    @Override
    public CorrectionResult handleAmbiguity(ParameterContext context, List<Object> candidates) {
        if (!supports(context) || candidates.isEmpty()) {
            return CorrectionResult.failed(context.getValueAsString(), "无有效候选");
        }

        List<String> corrections = Arrays.asList("处理多候选歧义");

        try {
            // 1. 如果只有一个候选，直接返回
            if (candidates.size() == 1) {
                return CorrectionResult.success(candidates.get(0), context.getValueAsString(), corrections, 0.8);
            }

            // 2. 限制候选数量
            List<Object> limitedCandidates = candidates.stream()
                    .limit(MAX_CANDIDATES)
                    .collect(Collectors.toList());

            // 3. 应用歧义解决策略
            Object bestCandidate = applyAmbiguityResolutionStrategy(context, limitedCandidates);

            if (bestCandidate != null) {
                // 找到最佳候选
                double confidence = calculateAmbiguityConfidence(context, limitedCandidates, bestCandidate);

                if (confidence > 0.7) {
                    // 高置信度，直接返回结果
                    return CorrectionResult.success(bestCandidate, context.getValueAsString(), corrections, confidence);
                } else {
                    // 低置信度，需要用户确认
                    return CorrectionResult.needsConfirmation(bestCandidate, context.getValueAsString(),
                            corrections, limitedCandidates);
                }
            }

            // 4. 无法确定最佳候选，返回需要确认的结果
            return CorrectionResult.needsConfirmation(limitedCandidates.get(0), context.getValueAsString(),
                    corrections, limitedCandidates);

        } catch (Exception e) {
            logger.warn("歧义处理失败: context={}, candidates={}", context.parameterName(), candidates.size(), e);
            return CorrectionResult.failed(context.getValueAsString(), "歧义处理异常: " + e.getMessage());
        }
    }

    @Override
    public boolean supports(ParameterContext context) {
        // 支持所有类型的歧义处理
        return true;
    }

    @Override
    public int getPriority() {
        return 10; // 高优先级，作为默认处理器
    }

    /**
     * 应用歧义解决策略
     */
    private Object applyAmbiguityResolutionStrategy(ParameterContext context, List<Object> candidates) {
        // 策略1: 基于字符串相似度
        Object similarityBest = findBestBySimilarity(context.getValueAsString(), candidates);
        if (similarityBest != null) {
            return similarityBest;
        }

        // 策略2: 基于类型优先级
        Object typeBest = findBestByTypePriority(context.parameterType(), candidates);
        if (typeBest != null) {
            return typeBest;
        }

        // 策略3: 基于长度或大小
        Object sizeBest = findBestBySize(candidates);
        if (sizeBest != null) {
            return sizeBest;
        }

        // 策略4: 基于常见性（频率）
        Object frequencyBest = findBestByFrequency(candidates);
        if (frequencyBest != null) {
            return frequencyBest;
        }

        // 默认返回第一个候选
        return candidates.get(0);
    }

    /**
     * 基于字符串相似度查找最佳候选
     */
    private Object findBestBySimilarity(String original, List<Object> candidates) {
        if (original == null || original.isEmpty()) {
            return null;
        }

        String originalLower = original.toLowerCase().trim();
        double maxSimilarity = 0.0;
        Object bestCandidate = null;

        for (Object candidate : candidates) {
            String candidateStr = candidate.toString().toLowerCase().trim();
            double similarity = calculateStringSimilarity(originalLower, candidateStr);

            if (similarity > maxSimilarity) {
                maxSimilarity = similarity;
                bestCandidate = candidate;
            }
        }

        // 只有相似度足够高才返回
        return maxSimilarity > 0.6 ? bestCandidate : null;
    }

    /**
     * 基于类型优先级查找最佳候选
     */
    private Object findBestByTypePriority(Class<?> targetType, List<Object> candidates) {
        // 类型优先级映射
        Map<Class<?>, Integer> typePriority = Map.of(
                String.class, 1,
                Integer.class, 2,
                Long.class, 3,
                Double.class, 4,
                Boolean.class, 5
        );

        return candidates.stream()
                .filter(candidate -> targetType.isAssignableFrom(candidate.getClass()))
                .min(Comparator.comparing(candidate ->
                        typePriority.getOrDefault(candidate.getClass(), Integer.MAX_VALUE)))
                .orElse(null);
    }

    /**
     * 基于大小查找最佳候选
     */
    private Object findBestBySize(List<Object> candidates) {
        // 对于字符串，选择长度适中的
        List<String> stringCandidates = candidates.stream()
                .filter(c -> c instanceof String)
                .map(String.class::cast)
                .collect(Collectors.toList());

        if (!stringCandidates.isEmpty()) {
            // 选择长度中位数的字符串
            stringCandidates.sort(Comparator.comparing(String::length));
            return stringCandidates.get(stringCandidates.size() / 2);
        }

        // 对于数值，选择中等大小的
        List<Number> numberCandidates = candidates.stream()
                .filter(c -> c instanceof Number)
                .map(Number.class::cast)
                .collect(Collectors.toList());

        if (!numberCandidates.isEmpty()) {
            numberCandidates.sort(Comparator.comparing(Number::doubleValue));
            return numberCandidates.get(numberCandidates.size() / 2);
        }

        return null;
    }

    /**
     * 基于频率查找最佳候选
     */
    private Object findBestByFrequency(List<Object> candidates) {
        // 简单的频率统计，选择最常见的类型
        Map<Class<?>, Long> typeFrequency = candidates.stream()
                .collect(Collectors.groupingBy(Object::getClass, Collectors.counting()));

        Optional<Class<?>> mostFrequentType = typeFrequency.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey);

        if (mostFrequentType.isPresent()) {
            return candidates.stream()
                    .filter(c -> c.getClass().equals(mostFrequentType.get()))
                    .findFirst()
                    .orElse(null);
        }

        return null;
    }

    /**
     * 计算歧义处理置信度
     */
    private double calculateAmbiguityConfidence(ParameterContext context, List<Object> candidates, Object bestCandidate) {
        double baseConfidence = 0.6;

        // 候选数量越少，置信度越高
        double candidatesPenalty = Math.min(0.3, candidates.size() * 0.05);
        baseConfidence -= candidatesPenalty;

        // 字符串相似度加成
        if (bestCandidate != null) {
            double similarity = calculateStringSimilarity(
                    context.getValueAsString().toLowerCase(),
                    bestCandidate.toString().toLowerCase()
            );
            baseConfidence += similarity * 0.3;
        }

        // 类型匹配加成
        if (bestCandidate != null && context.parameterType().isAssignableFrom(bestCandidate.getClass())) {
            baseConfidence += 0.1;
        }

        return Math.max(0.3, Math.min(0.9, baseConfidence));
    }

    /**
     * 计算字符串相似度
     */
    private double calculateStringSimilarity(String s1, String s2) {
        if (s1.equals(s2)) return 1.0;

        int maxLength = Math.max(s1.length(), s2.length());
        if (maxLength == 0) return 1.0;

        int distance = levenshteinDistance(s1, s2);
        return 1.0 - (double) distance / maxLength;
    }

    /**
     * 计算编辑距离
     */
    private int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];

        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }

        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = Math.min(Math.min(dp[i - 1][j], dp[i][j - 1]), dp[i - 1][j - 1]) + 1;
                }
            }
        }

        return dp[s1.length()][s2.length()];
    }
}