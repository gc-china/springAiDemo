package org.zerolg.aidemo2.correction.validator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.zerolg.aidemo2.correction.ParameterValidator;
import org.zerolg.aidemo2.correction.model.CorrectionResult;
import org.zerolg.aidemo2.correction.model.ParameterContext;

import jakarta.validation.constraints.*;

import java.lang.annotation.Annotation;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.zerolg.aidemo2.correction.annotation.PositiveNumber;

/**
 * 范围验证器
 * 验证参数是否在指定的范围内
 */
@Component
public class RangeValidator implements ParameterValidator {

    private static final Logger logger = LoggerFactory.getLogger(RangeValidator.class);

    @Override
    public CorrectionResult validate(ParameterContext context) {
        if (!supports(context)) {
            return CorrectionResult.noCorrection(context.originalValue());
        }

        Object value = context.originalValue();
        if (value == null) {
            return CorrectionResult.noCorrection(null);
        }

        List<String> corrections = new ArrayList<>();

        try {
            // 检查各种范围约束
            Object correctedValue = value;

            // 1. 数值范围验证
            correctedValue = validateNumericRange(correctedValue, context, corrections);

            // 2. 字符串长度验证
            correctedValue = validateStringLength(correctedValue, context, corrections);

            // 3. 集合大小验证
            correctedValue = validateCollectionSize(correctedValue, context, corrections);

            // 4. 日期范围验证
            correctedValue = validateDateRange(correctedValue, context, corrections);

            if (corrections.isEmpty()) {
                return CorrectionResult.noCorrection(value);
            }

            double confidence = calculateRangeValidationConfidence(corrections);
            return CorrectionResult.success(correctedValue, value.toString(), corrections, confidence);

        } catch (Exception e) {
            logger.warn("范围验证失败: value={}", value, e);
            return CorrectionResult.failed(value.toString(), "范围验证异常: " + e.getMessage());
        }
    }

    @Override
    public boolean supports(ParameterContext context) {
        // 检查参数是否有范围相关的注解
        if (context.parameter() == null) {
            return false;
        }

        Annotation[] annotations = context.parameter().getAnnotations();
        for (Annotation annotation : annotations) {
            if (isRangeAnnotation(annotation)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public int getPriority() {
        return 70; // 在类型验证之后执行
    }

    /**
     * 验证数值范围
     */
    private Object validateNumericRange(Object value, ParameterContext context, List<String> corrections) {
        if (!(value instanceof Number)) {
            return value;
        }

        Number numValue = (Number) value;

        // 检查 @Min 注解
        Min minAnnotation = context.parameter().getAnnotation(Min.class);
        if (minAnnotation != null) {
            long minValue = minAnnotation.value();
            if (numValue.longValue() < minValue) {
                corrections.add(String.format("调整到最小值: %d", minValue));
                return convertToTargetType(minValue, value.getClass());
            }
        }

        // 检查 @Max 注解
        Max maxAnnotation = context.parameter().getAnnotation(Max.class);
        if (maxAnnotation != null) {
            long maxValue = maxAnnotation.value();
            if (numValue.longValue() > maxValue) {
                corrections.add(String.format("调整到最大值: %d", maxValue));
                return convertToTargetType(maxValue, value.getClass());
            }
        }

        // 检查 @DecimalMin 注解
        DecimalMin decimalMinAnnotation = context.parameter().getAnnotation(DecimalMin.class);
        if (decimalMinAnnotation != null) {
            BigDecimal minValue = new BigDecimal(decimalMinAnnotation.value());
            BigDecimal currentValue = new BigDecimal(numValue.toString());

            if (currentValue.compareTo(minValue) < 0) {
                corrections.add(String.format("调整到最小值: %s", minValue));
                return convertToTargetType(minValue, value.getClass());
            }
        }

        // 检查 @DecimalMax 注解
        DecimalMax decimalMaxAnnotation = context.parameter().getAnnotation(DecimalMax.class);
        if (decimalMaxAnnotation != null) {
            BigDecimal maxValue = new BigDecimal(decimalMaxAnnotation.value());
            BigDecimal currentValue = new BigDecimal(numValue.toString());

            if (currentValue.compareTo(maxValue) > 0) {
                corrections.add(String.format("调整到最大值: %s", maxValue));
                return convertToTargetType(maxValue, value.getClass());
            }
        }

        // 注意：@Positive, @PositiveOrZero, @Negative, @NegativeOrZero 注解在某些版本中可能不可用
        // 这里可以通过自定义注解或其他方式实现类似功能

        // 检查自定义 @PositiveNumber 注解
        PositiveNumber positiveNumberAnnotation = context.parameter().getAnnotation(PositiveNumber.class);
        if (positiveNumberAnnotation != null) {
            boolean includeZero = positiveNumberAnnotation.includeZero();
            if ((!includeZero && numValue.doubleValue() <= 0) || (includeZero && numValue.doubleValue() < 0)) {
                corrections.add(includeZero ? "调整为非负数" : "调整为正数");
                return convertToTargetType(includeZero ? 0 : 1, value.getClass());
            }
        }

        // 可以通过检查参数名称来实现智能验证
        String paramName = context.parameterName().toLowerCase();
        if (paramName.contains("positive") && numValue.doubleValue() <= 0) {
            corrections.add("调整为正数");
            return convertToTargetType(Math.abs(numValue.doubleValue()) + 1, value.getClass());
        }

        if (paramName.contains("amount") && numValue.doubleValue() < 0) {
            corrections.add("调整为非负数");
            return convertToTargetType(Math.abs(numValue.doubleValue()), value.getClass());
        }

        return value;
    }

    /**
     * 验证字符串长度
     */
    private Object validateStringLength(Object value, ParameterContext context, List<String> corrections) {
        if (!(value instanceof String)) {
            return value;
        }

        String strValue = (String) value;

        // 检查 @Size 注解
        Size sizeAnnotation = context.parameter().getAnnotation(Size.class);
        if (sizeAnnotation != null) {
            int min = sizeAnnotation.min();
            int max = sizeAnnotation.max();
            int currentLength = strValue.length();

            if (currentLength < min) {
                // 字符串太短，填充空格或重复字符
                corrections.add(String.format("调整字符串长度到最小值: %d", min));
                return strValue + " ".repeat(min - currentLength);
            } else if (currentLength > max) {
                // 字符串太长，截断
                corrections.add(String.format("截断字符串到最大长度: %d", max));
                return strValue.substring(0, max);
            }
        }

        return value;
    }

    /**
     * 验证集合大小
     */
    private Object validateCollectionSize(Object value, ParameterContext context, List<String> corrections) {
        // 这里可以扩展支持集合类型的大小验证
        // 暂时跳过，因为需要更复杂的集合处理逻辑
        return value;
    }

    /**
     * 验证日期范围
     */
    private Object validateDateRange(Object value, ParameterContext context, List<String> corrections) {
        // 检查 @Past 注解
        Past pastAnnotation = context.parameter().getAnnotation(Past.class);
        if (pastAnnotation != null) {
            if (value instanceof LocalDate) {
                LocalDate dateValue = (LocalDate) value;
                if (!dateValue.isBefore(LocalDate.now())) {
                    corrections.add("调整为过去日期");
                    return LocalDate.now().minusDays(1);
                }
            } else if (value instanceof LocalDateTime) {
                LocalDateTime dateTimeValue = (LocalDateTime) value;
                if (!dateTimeValue.isBefore(LocalDateTime.now())) {
                    corrections.add("调整为过去时间");
                    return LocalDateTime.now().minusHours(1);
                }
            }
        }

        // 检查 @Future 注解
        Future futureAnnotation = context.parameter().getAnnotation(Future.class);
        if (futureAnnotation != null) {
            if (value instanceof LocalDate) {
                LocalDate dateValue = (LocalDate) value;
                if (!dateValue.isAfter(LocalDate.now())) {
                    corrections.add("调整为未来日期");
                    return LocalDate.now().plusDays(1);
                }
            } else if (value instanceof LocalDateTime) {
                LocalDateTime dateTimeValue = (LocalDateTime) value;
                if (!dateTimeValue.isAfter(LocalDateTime.now())) {
                    corrections.add("调整为未来时间");
                    return LocalDateTime.now().plusHours(1);
                }
            }
        }

        return value;
    }

    /**
     * 转换为目标数值类型
     */
    private Object convertToTargetType(Number value, Class<?> targetType) {
        if (Integer.class.equals(targetType) || int.class.equals(targetType)) {
            return value.intValue();
        } else if (Long.class.equals(targetType) || long.class.equals(targetType)) {
            return value.longValue();
        } else if (Double.class.equals(targetType) || double.class.equals(targetType)) {
            return value.doubleValue();
        } else if (Float.class.equals(targetType) || float.class.equals(targetType)) {
            return value.floatValue();
        } else if (BigDecimal.class.equals(targetType)) {
            if (value instanceof BigDecimal) {
                return value;
            }
            return BigDecimal.valueOf(value.doubleValue());
        }

        return value;
    }

    /**
     * 转换BigDecimal为目标类型
     */
    private Object convertToTargetType(BigDecimal value, Class<?> targetType) {
        if (Integer.class.equals(targetType) || int.class.equals(targetType)) {
            return value.intValue();
        } else if (Long.class.equals(targetType) || long.class.equals(targetType)) {
            return value.longValue();
        } else if (Double.class.equals(targetType) || double.class.equals(targetType)) {
            return value.doubleValue();
        } else if (Float.class.equals(targetType) || float.class.equals(targetType)) {
            return value.floatValue();
        } else if (BigDecimal.class.equals(targetType)) {
            return value;
        }

        return value;
    }

    /**
     * 检查是否为范围相关注解
     */
    private boolean isRangeAnnotation(Annotation annotation) {
        Class<?> annotationType = annotation.annotationType();
        return annotationType.equals(Min.class) ||
                annotationType.equals(Max.class) ||
                annotationType.equals(DecimalMin.class) ||
                annotationType.equals(DecimalMax.class) ||
                annotationType.equals(Size.class) ||
                annotationType.equals(Past.class) ||
                annotationType.equals(Future.class) ||
                annotationType.equals(PositiveNumber.class);
    }

    /**
     * 计算范围验证置信度
     */
    private double calculateRangeValidationConfidence(List<String> corrections) {
        double confidence = 0.8;

        for (String correction : corrections) {
            if (correction.contains("截断") || correction.contains("调整")) {
                confidence -= 0.1;
            }
        }

        return Math.max(0.6, confidence);
    }
}