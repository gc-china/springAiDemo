package org.zerolg.aidemo2.example;

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
 * 自定义电话号码标准化器
 * 演示如何扩展参数修正系统
 */
@Component
public class CustomPhoneNormalizer implements ParamNormalizer {

    private static final Logger logger = LoggerFactory.getLogger(CustomPhoneNormalizer.class);

    // 电话号码相关的模式
    private static final Pattern PHONE_PATTERN = Pattern.compile(".*phone.*|.*tel.*|.*mobile.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHINESE_PHONE_PATTERN = Pattern.compile(".*电话.*|.*手机.*|.*联系.*");

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

        // 1. 移除所有非数字字符（除了+号）
        String digitsOnly = normalized.replaceAll("[^\\d+]", "");
        if (!digitsOnly.equals(normalized)) {
            corrections.add("移除非数字字符");
            normalized = digitsOnly;
        }

        // 2. 处理国际区号
        if (normalized.startsWith("86") && normalized.length() == 13) {
            normalized = "+" + normalized;
            corrections.add("添加国际区号前缀");
        } else if (normalized.startsWith("0") && normalized.length() == 11) {
            // 移除前导0
            normalized = normalized.substring(1);
            corrections.add("移除前导0");
        }

        // 3. 验证手机号码格式
        if (normalized.length() == 11 && normalized.startsWith("1")) {
            // 中国手机号码格式：1xx-xxxx-xxxx
            String formatted = normalized.substring(0, 3) + "-" +
                    normalized.substring(3, 7) + "-" +
                    normalized.substring(7);
            if (!formatted.equals(normalized)) {
                corrections.add("格式化手机号码");
                normalized = formatted;
            }
        }

        // 4. 验证固定电话格式
        if (normalized.length() >= 7 && normalized.length() <= 8 && !normalized.startsWith("1")) {
            // 可能是固定电话，添加区号提示
            corrections.add("可能需要添加区号");
        }

        if (corrections.isEmpty()) {
            return CorrectionResult.noCorrection(originalValue);
        }

        // 计算置信度
        double confidence = calculateConfidence(originalValue, normalized, corrections);

        logger.debug("电话号码标准化: '{}' -> '{}', 应用修正: {}", originalValue, normalized, corrections);

        return CorrectionResult.success(normalized, originalValue, corrections, confidence);
    }

    @Override
    public boolean supports(ParameterContext context) {
        // 检查参数名是否包含电话相关关键词
        String paramName = context.parameterName().toLowerCase();
        return PHONE_PATTERN.matcher(paramName).matches() ||
                CHINESE_PHONE_PATTERN.matcher(paramName).find();
    }

    @Override
    public int getPriority() {
        return 25; // 在基础标准化之后，验证之前执行
    }

    /**
     * 计算置信度
     */
    private double calculateConfidence(String original, String normalized, List<String> corrections) {
        double confidence = 0.8;

        // 根据修正类型调整置信度
        for (String correction : corrections) {
            switch (correction) {
                case "移除非数字字符" -> confidence -= 0.1;
                case "格式化手机号码" -> confidence += 0.1;
                case "可能需要添加区号" -> confidence -= 0.3;
                default -> confidence -= 0.05;
            }
        }

        return Math.max(0.4, Math.min(0.95, confidence));
    }
}