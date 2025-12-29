package org.zerolg.aidemo2.correction.resolver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.zerolg.aidemo2.correction.EntityResolver;
import org.zerolg.aidemo2.correction.model.CorrectionResult;
import org.zerolg.aidemo2.correction.model.ParameterContext;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 默认实体解析器
 * 提供基础的模糊匹配和实体解析功能
 */
@Component
public class DefaultEntityResolver implements EntityResolver {

    private static final Logger logger = LoggerFactory.getLogger(DefaultEntityResolver.class);

    // 预定义的实体映射
    private static final Map<String, List<String>> ENTITY_MAPPINGS = new HashMap<>();

    static {
        // 布尔值映射
        ENTITY_MAPPINGS.put("boolean", Arrays.asList(
                "true", "false", "是", "否", "对", "错", "有", "无",
                "启用", "禁用", "开启", "关闭", "1", "0", "yes", "no"
        ));

        // 状态映射
        ENTITY_MAPPINGS.put("status", Arrays.asList(
                "active", "inactive", "enabled", "disabled", "pending", "completed",
                "激活", "未激活", "启用", "禁用", "待处理", "已完成"
        ));

        // 优先级映射
        ENTITY_MAPPINGS.put("priority", Arrays.asList(
                "high", "medium", "low", "urgent", "normal",
                "高", "中", "低", "紧急", "普通"
        ));
    }

    @Override
    public CorrectionResult resolve(ParameterContext context) {
        if (!supports(context.parameterType())) {
            return CorrectionResult.noCorrection(context.originalValue());
        }

        String input = context.getValueAsString().toLowerCase().trim();
        if (input.isEmpty()) {
            return CorrectionResult.noCorrection(context.originalValue());
        }

        List<String> corrections = new ArrayList<>();

        try {
            // 1. 直接匹配
            Object directMatch = findDirectMatch(input, context.parameterType());
            if (directMatch != null) {
                if (!directMatch.equals(context.originalValue())) {
                    corrections.add("直接实体匹配");
                    return CorrectionResult.success(directMatch, context.getValueAsString(), corrections, 0.95);
                }
                return CorrectionResult.noCorrection(context.originalValue());
            }

            // 2. 模糊匹配
            List<Object> candidates = fuzzyMatch(input, context.parameterType());
            if (candidates.isEmpty()) {
                return CorrectionResult.failed(context.getValueAsString(), "未找到匹配的实体");
            }

            if (candidates.size() == 1) {
                corrections.add("模糊实体匹配");
                return CorrectionResult.success(candidates.get(0), context.getValueAsString(), corrections, 0.8);
            }

            // 3. 多个候选，需要确认
            corrections.add("多候选实体匹配");
            return CorrectionResult.needsConfirmation(candidates.get(0), context.getValueAsString(), corrections, candidates);

        } catch (Exception e) {
            logger.warn("实体解析失败: '{}'", input, e);
            return CorrectionResult.failed(context.getValueAsString(), "实体解析异常: " + e.getMessage());
        }
    }

    @Override
    public List<Object> fuzzyMatch(String input, Class<?> entityType) {
        String normalizedInput = input.toLowerCase().trim();
        List<Object> candidates = new ArrayList<>();

        // 布尔类型特殊处理
        if (Boolean.class.equals(entityType) || boolean.class.equals(entityType)) {
            return fuzzyMatchBoolean(normalizedInput);
        }

        // 枚举类型处理
        if (entityType.isEnum()) {
            return fuzzyMatchEnum(normalizedInput, entityType);
        }

        // 字符串类型的预定义实体
        if (String.class.equals(entityType)) {
            return fuzzyMatchPredefinedEntities(normalizedInput);
        }

        return candidates;
    }

    @Override
    public boolean supports(Class<?> entityType) {
        return Boolean.class.equals(entityType) ||
                boolean.class.equals(entityType) ||
                entityType.isEnum() ||
                String.class.equals(entityType);
    }

    @Override
    public int getPriority() {
        return 50; // 中等优先级
    }

    /**
     * 查找直接匹配
     */
    private Object findDirectMatch(String input, Class<?> entityType) {
        // 布尔类型
        if (Boolean.class.equals(entityType) || boolean.class.equals(entityType)) {
            return parseBoolean(input);
        }

        // 枚举类型
        if (entityType.isEnum()) {
            return findEnumMatch(input, entityType);
        }

        return null;
    }

    /**
     * 模糊匹配布尔值
     */
    private List<Object> fuzzyMatchBoolean(String input) {
        List<Object> candidates = new ArrayList<>();

        // 真值匹配
        List<String> trueValues = Arrays.asList("true", "是", "对", "有", "启用", "开启", "1", "yes", "y", "t");
        List<String> falseValues = Arrays.asList("false", "否", "错", "无", "禁用", "关闭", "0", "no", "n", "f");

        double trueScore = calculateSimilarity(input, trueValues);
        double falseScore = calculateSimilarity(input, falseValues);

        if (trueScore > 0.6 || falseScore > 0.6) {
            if (trueScore > falseScore) {
                candidates.add(true);
            } else {
                candidates.add(false);
            }
        }

        return candidates;
    }

    /**
     * 模糊匹配枚举
     */
    private List<Object> fuzzyMatchEnum(String input, Class<?> enumType) {
        List<Object> candidates = new ArrayList<>();
        Object[] enumConstants = enumType.getEnumConstants();

        Map<Object, Double> scores = new HashMap<>();

        for (Object enumConstant : enumConstants) {
            String enumName = enumConstant.toString().toLowerCase();
            double score = calculateStringSimilarity(input, enumName);

            if (score > 0.6) {
                scores.put(enumConstant, score);
            }
        }

        // 按相似度排序
        return scores.entrySet().stream()
                .sorted(Map.Entry.<Object, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .limit(3)
                .collect(Collectors.toList());
    }

    /**
     * 模糊匹配预定义实体
     */
    private List<Object> fuzzyMatchPredefinedEntities(String input) {
        List<Object> candidates = new ArrayList<>();

        for (Map.Entry<String, List<String>> entry : ENTITY_MAPPINGS.entrySet()) {
            double score = calculateSimilarity(input, entry.getValue());
            if (score > 0.7) {
                // 找到最匹配的值
                String bestMatch = entry.getValue().stream()
                        .max(Comparator.comparing(value -> calculateStringSimilarity(input, value)))
                        .orElse(null);

                if (bestMatch != null) {
                    candidates.add(bestMatch);
                }
            }
        }

        return candidates;
    }

    /**
     * 解析布尔值
     */
    private Boolean parseBoolean(String input) {
        List<String> trueValues = Arrays.asList("true", "是", "对", "有", "启用", "开启", "1", "yes");
        List<String> falseValues = Arrays.asList("false", "否", "错", "无", "禁用", "关闭", "0", "no");

        if (trueValues.contains(input)) {
            return true;
        } else if (falseValues.contains(input)) {
            return false;
        }

        return null;
    }

    /**
     * 查找枚举匹配
     */
    private Object findEnumMatch(String input, Class<?> enumType) {
        Object[] enumConstants = enumType.getEnumConstants();

        for (Object enumConstant : enumConstants) {
            if (enumConstant.toString().equalsIgnoreCase(input)) {
                return enumConstant;
            }
        }

        return null;
    }

    /**
     * 计算与值列表的相似度
     */
    private double calculateSimilarity(String input, List<String> values) {
        return values.stream()
                .mapToDouble(value -> calculateStringSimilarity(input, value))
                .max()
                .orElse(0.0);
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