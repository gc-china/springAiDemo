package org.zerolg.aidemo2.correction.normalizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.zerolg.aidemo2.correction.ParamNormalizer;
import org.zerolg.aidemo2.correction.model.CorrectionResult;
import org.zerolg.aidemo2.correction.model.ParameterContext;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 数值标准化器
 * 负责清理和标准化数值参数
 */
@Component
public class NumberNormalizer implements ParamNormalizer {

    private static final Logger logger = LoggerFactory.getLogger(NumberNormalizer.class);

    // 数值模式
    private static final Pattern CHINESE_NUMBERS = Pattern.compile("[零一二三四五六七八九十百千万亿壹贰叁肆伍陆柒捌玖拾佰仟萬億]");
    private static final Pattern CURRENCY_SYMBOLS = Pattern.compile("[¥$€£￥]");
    private static final Pattern PERCENTAGE = Pattern.compile("%");
    private static final Pattern COMMA_THOUSANDS = Pattern.compile(",");
    private static final Pattern EXTRA_SPACES = Pattern.compile("\\s+");

    @Override
    public CorrectionResult normalize(ParameterContext context) {
        if (!supports(context)) {
            return CorrectionResult.noCorrection(context.originalValue());
        }

        String originalValue = context.getValueAsString().trim();
        if (originalValue.isEmpty()) {
            return CorrectionResult.noCorrection(context.originalValue());
        }

        List<String> corrections = new ArrayList<>();
        String normalized = originalValue;

        try {
            // 1. 移除多余空格
            String spacesRemoved = EXTRA_SPACES.matcher(normalized).replaceAll("");
            if (!spacesRemoved.equals(normalized)) {
                corrections.add("移除空格");
                normalized = spacesRemoved;
            }

            // 2. 处理货币符号
            if (CURRENCY_SYMBOLS.matcher(normalized).find()) {
                normalized = CURRENCY_SYMBOLS.matcher(normalized).replaceAll("");
                corrections.add("移除货币符号");
            }

            // 3. 处理百分号
            boolean isPercentage = PERCENTAGE.matcher(normalized).find();
            if (isPercentage) {
                normalized = PERCENTAGE.matcher(normalized).replaceAll("");
                corrections.add("处理百分比");
            }

            // 4. 处理千位分隔符
            if (COMMA_THOUSANDS.matcher(normalized).find()) {
                normalized = COMMA_THOUSANDS.matcher(normalized).replaceAll("");
                corrections.add("移除千位分隔符");
            }

            // 5. 处理中文数字
            if (CHINESE_NUMBERS.matcher(normalized).find()) {
                normalized = convertChineseNumbers(normalized);
                corrections.add("转换中文数字");
            }

            // 6. 尝试解析数值
            Number parsedNumber = parseNumber(normalized, context.parameterType());

            // 7. 如果是百分比，转换为小数
            if (isPercentage && parsedNumber != null) {
                if (parsedNumber instanceof Double) {
                    parsedNumber = parsedNumber.doubleValue() / 100.0;
                } else if (parsedNumber instanceof Float) {
                    parsedNumber = parsedNumber.floatValue() / 100.0f;
                } else if (parsedNumber instanceof BigDecimal) {
                    parsedNumber = ((BigDecimal) parsedNumber).divide(BigDecimal.valueOf(100));
                }
            }

            if (parsedNumber == null) {
                return CorrectionResult.failed(originalValue, "无法解析为有效数值");
            }

            // 计算置信度
            double confidence = calculateConfidence(originalValue, normalized, corrections);

            if (corrections.isEmpty() && parsedNumber.equals(context.originalValue())) {
                return CorrectionResult.noCorrection(context.originalValue());
            }

            logger.debug("数值标准化: '{}' -> {}, 应用修正: {}", originalValue, parsedNumber, corrections);

            return CorrectionResult.success(parsedNumber, originalValue, corrections, confidence);

        } catch (Exception e) {
            logger.warn("数值标准化失败: '{}'", originalValue, e);
            return CorrectionResult.failed(originalValue, "数值解析异常: " + e.getMessage());
        }
    }

    @Override
    public boolean supports(ParameterContext context) {
        return context.isNumericType();
    }

    @Override
    public int getPriority() {
        return 20; // 在字符串清理之后执行
    }

    /**
     * 解析数值
     */
    private Number parseNumber(String value, Class<?> targetType) {
        try {
            // 尝试不同的数值格式
            NumberFormat[] formats = {
                    NumberFormat.getInstance(Locale.US),
                    NumberFormat.getInstance(Locale.CHINA),
                    DecimalFormat.getInstance()
            };

            for (NumberFormat format : formats) {
                try {
                    Number parsed = format.parse(value);
                    return convertToTargetType(parsed, targetType);
                } catch (ParseException ignored) {
                    // 继续尝试下一个格式
                }
            }

            // 直接解析
            if (targetType.equals(Integer.class) || targetType.equals(int.class)) {
                return Integer.parseInt(value);
            } else if (targetType.equals(Long.class) || targetType.equals(long.class)) {
                return Long.parseLong(value);
            } else if (targetType.equals(Double.class) || targetType.equals(double.class)) {
                return Double.parseDouble(value);
            } else if (targetType.equals(Float.class) || targetType.equals(float.class)) {
                return Float.parseFloat(value);
            } else if (targetType.equals(BigDecimal.class)) {
                return new BigDecimal(value);
            }

        } catch (NumberFormatException e) {
            logger.debug("数值解析失败: '{}'", value, e);
        }

        return null;
    }

    /**
     * 转换为目标类型
     */
    private Number convertToTargetType(Number number, Class<?> targetType) {
        if (targetType.equals(Integer.class) || targetType.equals(int.class)) {
            return number.intValue();
        } else if (targetType.equals(Long.class) || targetType.equals(long.class)) {
            return number.longValue();
        } else if (targetType.equals(Double.class) || targetType.equals(double.class)) {
            return number.doubleValue();
        } else if (targetType.equals(Float.class) || targetType.equals(float.class)) {
            return number.floatValue();
        } else if (targetType.equals(BigDecimal.class)) {
            if (number instanceof BigDecimal) {
                return number;
            }
            return BigDecimal.valueOf(number.doubleValue());
        }

        return number;
    }

    /**
     * 转换中文数字
     */
    private String convertChineseNumbers(String input) {
        // 简单的中文数字转换
        String result = input;

        // 基本数字
        result = result.replace("零", "0")
                .replace("一", "1")
                .replace("二", "2")
                .replace("三", "3")
                .replace("四", "4")
                .replace("五", "5")
                .replace("六", "6")
                .replace("七", "7")
                .replace("八", "8")
                .replace("九", "9");

        // 繁体数字
        result = result.replace("壹", "1")
                .replace("贰", "2")
                .replace("叁", "3")
                .replace("肆", "4")
                .replace("伍", "5")
                .replace("陆", "6")
                .replace("柒", "7")
                .replace("捌", "8")
                .replace("玖", "9");

        // 单位转换（简化处理）
        result = result.replace("十", "10")
                .replace("拾", "10")
                .replace("百", "00")
                .replace("佰", "00")
                .replace("千", "000")
                .replace("仟", "000")
                .replace("万", "0000")
                .replace("萬", "0000")
                .replace("亿", "00000000")
                .replace("億", "00000000");

        return result;
    }

    /**
     * 计算标准化置信度
     */
    private double calculateConfidence(String original, String normalized, List<String> corrections) {
        if (corrections.isEmpty()) {
            return 1.0;
        }

        // 基于修正类型调整置信度
        double confidence = 0.9;

        for (String correction : corrections) {
            switch (correction) {
                case "移除空格", "移除千位分隔符" -> confidence -= 0.05;
                case "移除货币符号", "处理百分比" -> confidence -= 0.1;
                case "转换中文数字" -> confidence -= 0.2;
                default -> confidence -= 0.1;
            }
        }

        return Math.max(0.5, confidence);
    }
}