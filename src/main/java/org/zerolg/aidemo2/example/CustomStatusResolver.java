package org.zerolg.aidemo2.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.zerolg.aidemo2.correction.EntityResolver;
import org.zerolg.aidemo2.correction.model.CorrectionResult;
import org.zerolg.aidemo2.correction.model.ParameterContext;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 自定义状态解析器
 * 演示如何扩展实体解析功能
 */
@Component
public class CustomStatusResolver implements EntityResolver {

    private static final Logger logger = LoggerFactory.getLogger(CustomStatusResolver.class);

    // 预定义的状态映射
    private static final Map<String, String> STATUS_MAPPINGS = new HashMap<>();

    static {
        // 订单状态
        STATUS_MAPPINGS.put("待付款", "PENDING_PAYMENT");
        STATUS_MAPPINGS.put("已付款", "PAID");
        STATUS_MAPPINGS.put("已发货", "SHIPPED");
        STATUS_MAPPINGS.put("已完成", "COMPLETED");
        STATUS_MAPPINGS.put("已取消", "CANCELLED");

        // 用户状态
        STATUS_MAPPINGS.put("正常", "ACTIVE");
        STATUS_MAPPINGS.put("冻结", "FROZEN");
        STATUS_MAPPINGS.put("注销", "DELETED");

        // 审核状态
        STATUS_MAPPINGS.put("待审核", "PENDING_REVIEW");
        STATUS_MAPPINGS.put("审核通过", "APPROVED");
        STATUS_MAPPINGS.put("审核拒绝", "REJECTED");
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
            String directMatch = findDirectMatch(input);
            if (directMatch != null) {
                corrections.add("直接状态匹配");
                return CorrectionResult.success(directMatch, context.getValueAsString(), corrections, 0.95);
            }

            // 2. 模糊匹配
            List<Object> candidates = fuzzyMatch(input, context.parameterType());
            if (candidates.isEmpty()) {
                return CorrectionResult.failed(context.getValueAsString(), "未找到匹配的状态");
            }

            if (candidates.size() == 1) {
                corrections.add("模糊状态匹配");
                return CorrectionResult.success(candidates.get(0), context.getValueAsString(), corrections, 0.8);
            }

            // 3. 多个候选
            corrections.add("多候选状态匹配");
            return CorrectionResult.needsConfirmation(candidates.get(0), context.getValueAsString(), corrections, candidates);

        } catch (Exception e) {
            logger.warn("状态解析失败: '{}'", input, e);
            return CorrectionResult.failed(context.getValueAsString(), "状态解析异常: " + e.getMessage());
        }
    }

    @Override
    public List<Object> fuzzyMatch(String input, Class<?> entityType) {
        List<Object> candidates = new ArrayList<>();

        // 计算与所有状态的相似度
        Map<String, Double> similarities = new HashMap<>();

        for (Map.Entry<String, String> entry : STATUS_MAPPINGS.entrySet()) {
            String chineseStatus = entry.getKey();
            String englishStatus = entry.getValue();

            // 计算与中文状态的相似度
            double chineseSimilarity = calculateSimilarity(input, chineseStatus.toLowerCase());
            // 计算与英文状态的相似度
            double englishSimilarity = calculateSimilarity(input, englishStatus.toLowerCase());

            double maxSimilarity = Math.max(chineseSimilarity, englishSimilarity);

            if (maxSimilarity > 0.6) {
                similarities.put(englishStatus, maxSimilarity);
            }
        }

        // 按相似度排序，返回前3个
        return similarities.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(3)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    @Override
    public boolean supports(Class<?> entityType) {
        // 支持字符串类型，且参数名包含status相关关键词
        return String.class.equals(entityType);
    }

    @Override
    public int getPriority() {
        return 40; // 在默认解析器之前执行
    }

    /**
     * 查找直接匹配
     */
    private String findDirectMatch(String input) {
        // 直接匹配中文
        String englishStatus = STATUS_MAPPINGS.get(input);
        if (englishStatus != null) {
            return englishStatus;
        }

        // 直接匹配英文
        for (String status : STATUS_MAPPINGS.values()) {
            if (status.toLowerCase().equals(input)) {
                return status;
            }
        }

        return null;
    }

    /**
     * 计算字符串相似度
     */
    private double calculateSimilarity(String s1, String s2) {
        if (s1.equals(s2)) return 1.0;

        // 包含关系检查
        if (s1.contains(s2) || s2.contains(s1)) {
            return 0.8;
        }

        // 编辑距离计算
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