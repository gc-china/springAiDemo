package org.zerolg.aidemo2.correction.normalizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.zerolg.aidemo2.correction.ParamNormalizer;
import org.zerolg.aidemo2.correction.model.CorrectionResult;
import org.zerolg.aidemo2.correction.model.ParameterContext;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 字符串标准化器
 * 负责清理和标准化字符串参数
 */
@Component
public class StringNormalizer implements ParamNormalizer {

    private static final Logger logger = LoggerFactory.getLogger(StringNormalizer.class);

    // 常见的清理模式
    private static final Pattern EXTRA_SPACES = Pattern.compile("\\s+");
    private static final Pattern SPECIAL_QUOTES = Pattern.compile("[''`´]");
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x1F\\x7F]");
    private static final Pattern HTML_TAGS = Pattern.compile("<[^>]+>");

    @Override
    public CorrectionResult normalize(ParameterContext context) {
        if (!supports(context)) {
            return CorrectionResult.noCorrection(context.originalValue());
        }

        String originalValue = context.getValueAsString();
        if (originalValue == null || originalValue.isEmpty()) {
            return CorrectionResult.noCorrection(originalValue);
        }

        List<String> corrections = new ArrayList<>();
        String normalized = originalValue;

        // 1. 移除前后空白
        String trimmed = normalized.trim();
        if (!trimmed.equals(normalized)) {
            corrections.add("移除前后空白");
            normalized = trimmed;
        }

        // 2. 标准化空白字符
        String spacesNormalized = EXTRA_SPACES.matcher(normalized).replaceAll(" ");
        if (!spacesNormalized.equals(normalized)) {
            corrections.add("标准化空白字符");
            normalized = spacesNormalized;
        }

        // 3. 标准化引号
        String quotesNormalized = SPECIAL_QUOTES.matcher(normalized).replaceAll("\"");
        if (!quotesNormalized.equals(normalized)) {
            corrections.add("标准化引号");
            normalized = quotesNormalized;
        }

        // 4. 移除控制字符
        String controlCharsRemoved = CONTROL_CHARS.matcher(normalized).replaceAll("");
        if (!controlCharsRemoved.equals(normalized)) {
            corrections.add("移除控制字符");
            normalized = controlCharsRemoved;
        }

        // 5. 移除HTML标签（如果存在）
        String htmlRemoved = HTML_TAGS.matcher(normalized).replaceAll("");
        if (!htmlRemoved.equals(normalized)) {
            corrections.add("移除HTML标签");
            normalized = htmlRemoved;
        }

        // 6. 处理常见的编码问题
        normalized = fixCommonEncodingIssues(normalized, corrections);

        // 计算置信度
        double confidence = calculateConfidence(originalValue, normalized, corrections);

        if (corrections.isEmpty()) {
            return CorrectionResult.noCorrection(originalValue);
        }

        logger.debug("字符串标准化: '{}' -> '{}', 应用修正: {}", originalValue, normalized, corrections);

        return CorrectionResult.success(normalized, originalValue, corrections, confidence);
    }

    @Override
    public boolean supports(ParameterContext context) {
        return context.isStringType() || context.originalValue() instanceof String;
    }

    @Override
    public int getPriority() {
        return 10; // 高优先级，首先执行字符串清理
    }

    /**
     * 修复常见的编码问题
     */
    private String fixCommonEncodingIssues(String input, List<String> corrections) {
        String result = input;

        // 修复常见的UTF-8编码问题
        if (result.contains("â€™")) {
            result = result.replace("â€™", "'");
            corrections.add("修复UTF-8编码问题");
        }

        if (result.contains("â€œ")) {
            result = result.replace("â€œ", "\"");
            corrections.add("修复UTF-8编码问题");
        }

        if (result.contains("â€")) {
            result = result.replace("â€", "\"");
            corrections.add("修复UTF-8编码问题");
        }

        // 修复全角字符
        result = result.replace("（", "(")
                .replace("）", ")")
                .replace("，", ",")
                .replace("。", ".")
                .replace("：", ":")
                .replace("；", ";");

        if (!result.equals(input)) {
            corrections.add("标准化标点符号");
        }

        return result;
    }

    /**
     * 计算标准化置信度
     */
    private double calculateConfidence(String original, String normalized, List<String> corrections) {
        if (corrections.isEmpty()) {
            return 1.0;
        }

        // 基于修改程度计算置信度
        double similarity = calculateSimilarity(original, normalized);

        // 基于修正类型调整置信度
        double correctionPenalty = corrections.size() * 0.05; // 每个修正降低5%置信度

        return Math.max(0.5, similarity - correctionPenalty);
    }

    /**
     * 计算字符串相似度
     */
    private double calculateSimilarity(String s1, String s2) {
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