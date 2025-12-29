package org.zerolg.aidemo2.correction.normalizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.zerolg.aidemo2.correction.ParamNormalizer;
import org.zerolg.aidemo2.correction.model.CorrectionResult;
import org.zerolg.aidemo2.correction.model.ParameterContext;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 日期时间标准化器
 * 负责清理和标准化日期时间参数
 */
@Component
public class DateNormalizer implements ParamNormalizer {

    private static final Logger logger = LoggerFactory.getLogger(DateNormalizer.class);

    // 常见日期格式
    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyy.MM.dd"),
            DateTimeFormatter.ofPattern("yyyy年MM月dd日"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("yyyyMMdd")
    );

    // 常见日期时间格式
    private static final List<DateTimeFormatter> DATETIME_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH时mm分ss秒"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
    );

    // 时间格式
    private static final List<DateTimeFormatter> TIME_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("HH:mm:ss"),
            DateTimeFormatter.ofPattern("HH:mm"),
            DateTimeFormatter.ofPattern("HH时mm分ss秒"),
            DateTimeFormatter.ofPattern("HH时mm分")
    );

    // 清理模式
    private static final Pattern EXTRA_SPACES = Pattern.compile("\\s+");
    private static final Pattern CHINESE_CHARS = Pattern.compile("[年月日时分秒]");

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
            // 1. 标准化空格
            String spacesNormalized = EXTRA_SPACES.matcher(normalized).replaceAll(" ");
            if (!spacesNormalized.equals(normalized)) {
                corrections.add("标准化空格");
                normalized = spacesNormalized;
            }

            // 2. 处理相对日期表达
            normalized = handleRelativeDates(normalized, corrections);

            // 3. 标准化中文日期格式
            normalized = normalizeChineseDateFormat(normalized, corrections);

            // 4. 尝试解析日期时间
            Object parsedDateTime = parseDateTime(normalized, context.parameterType());

            if (parsedDateTime == null) {
                return CorrectionResult.failed(originalValue, "无法解析为有效日期时间");
            }

            // 计算置信度
            double confidence = calculateConfidence(originalValue, normalized, corrections);

            if (corrections.isEmpty() && parsedDateTime.equals(context.originalValue())) {
                return CorrectionResult.noCorrection(context.originalValue());
            }

            logger.debug("日期时间标准化: '{}' -> {}, 应用修正: {}", originalValue, parsedDateTime, corrections);

            return CorrectionResult.success(parsedDateTime, originalValue, corrections, confidence);

        } catch (Exception e) {
            logger.warn("日期时间标准化失败: '{}'", originalValue, e);
            return CorrectionResult.failed(originalValue, "日期时间解析异常: " + e.getMessage());
        }
    }

    @Override
    public boolean supports(ParameterContext context) {
        Class<?> type = context.parameterType();
        return LocalDate.class.equals(type) ||
                LocalDateTime.class.equals(type) ||
                LocalTime.class.equals(type) ||
                ZonedDateTime.class.equals(type) ||
                Instant.class.equals(type) ||
                java.util.Date.class.equals(type) ||
                java.sql.Date.class.equals(type) ||
                java.sql.Timestamp.class.equals(type);
    }

    @Override
    public int getPriority() {
        return 30; // 在基础清理之后执行
    }

    /**
     * 处理相对日期表达
     */
    private String handleRelativeDates(String input, List<String> corrections) {
        String result = input.toLowerCase();
        LocalDate today = LocalDate.now();

        if (result.contains("今天") || result.equals("today")) {
            corrections.add("转换相对日期表达");
            return today.toString();
        } else if (result.contains("昨天") || result.equals("yesterday")) {
            corrections.add("转换相对日期表达");
            return today.minusDays(1).toString();
        } else if (result.contains("明天") || result.equals("tomorrow")) {
            corrections.add("转换相对日期表达");
            return today.plusDays(1).toString();
        } else if (result.contains("上周") || result.contains("last week")) {
            corrections.add("转换相对日期表达");
            return today.minusWeeks(1).toString();
        } else if (result.contains("下周") || result.contains("next week")) {
            corrections.add("转换相对日期表达");
            return today.plusWeeks(1).toString();
        } else if (result.contains("上个月") || result.contains("last month")) {
            corrections.add("转换相对日期表达");
            return today.minusMonths(1).toString();
        } else if (result.contains("下个月") || result.contains("next month")) {
            corrections.add("转换相对日期表达");
            return today.plusMonths(1).toString();
        }

        return input;
    }

    /**
     * 标准化中文日期格式
     */
    private String normalizeChineseDateFormat(String input, List<String> corrections) {
        if (!CHINESE_CHARS.matcher(input).find()) {
            return input;
        }

        corrections.add("标准化中文日期格式");

        // 替换中文字符为标准分隔符
        return input.replace("年", "-")
                .replace("月", "-")
                .replace("日", "")
                .replace("时", ":")
                .replace("分", ":")
                .replace("秒", "");
    }

    /**
     * 解析日期时间
     */
    private Object parseDateTime(String value, Class<?> targetType) {
        // 尝试解析为不同的日期时间类型
        if (LocalDate.class.equals(targetType)) {
            return parseLocalDate(value);
        } else if (LocalDateTime.class.equals(targetType)) {
            return parseLocalDateTime(value);
        } else if (LocalTime.class.equals(targetType)) {
            return parseLocalTime(value);
        } else if (ZonedDateTime.class.equals(targetType)) {
            LocalDateTime ldt = parseLocalDateTime(value);
            return ldt != null ? ldt.atZone(ZoneId.systemDefault()) : null;
        } else if (Instant.class.equals(targetType)) {
            LocalDateTime ldt = parseLocalDateTime(value);
            return ldt != null ? ldt.atZone(ZoneId.systemDefault()).toInstant() : null;
        } else if (java.util.Date.class.equals(targetType)) {
            LocalDateTime ldt = parseLocalDateTime(value);
            return ldt != null ? java.util.Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant()) : null;
        } else if (java.sql.Date.class.equals(targetType)) {
            LocalDate ld = parseLocalDate(value);
            return ld != null ? java.sql.Date.valueOf(ld) : null;
        } else if (java.sql.Timestamp.class.equals(targetType)) {
            LocalDateTime ldt = parseLocalDateTime(value);
            return ldt != null ? java.sql.Timestamp.valueOf(ldt) : null;
        }

        return null;
    }

    /**
     * 解析LocalDate
     */
    private LocalDate parseLocalDate(String value) {
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // 继续尝试下一个格式
            }
        }
        return null;
    }

    /**
     * 解析LocalDateTime
     */
    private LocalDateTime parseLocalDateTime(String value) {
        // 首先尝试日期时间格式
        for (DateTimeFormatter formatter : DATETIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // 继续尝试下一个格式
            }
        }

        // 如果只有日期，添加默认时间
        LocalDate date = parseLocalDate(value);
        if (date != null) {
            return date.atStartOfDay();
        }

        return null;
    }

    /**
     * 解析LocalTime
     */
    private LocalTime parseLocalTime(String value) {
        for (DateTimeFormatter formatter : TIME_FORMATTERS) {
            try {
                return LocalTime.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // 继续尝试下一个格式
            }
        }
        return null;
    }

    /**
     * 计算标准化置信度
     */
    private double calculateConfidence(String original, String normalized, List<String> corrections) {
        if (corrections.isEmpty()) {
            return 1.0;
        }

        double confidence = 0.9;

        for (String correction : corrections) {
            switch (correction) {
                case "标准化空格" -> confidence -= 0.05;
                case "标准化中文日期格式" -> confidence -= 0.1;
                case "转换相对日期表达" -> confidence -= 0.15;
                default -> confidence -= 0.1;
            }
        }

        return Math.max(0.6, confidence);
    }
}