package org.zerolg.aidemo2.correction.validator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.zerolg.aidemo2.correction.ParameterValidator;
import org.zerolg.aidemo2.correction.model.CorrectionResult;
import org.zerolg.aidemo2.correction.model.ParameterContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 类型验证器
 * 验证参数是否符合目标类型要求
 */
@Component
public class TypeValidator implements ParameterValidator {

    private static final Logger logger = LoggerFactory.getLogger(TypeValidator.class);

    @Override
    public CorrectionResult validate(ParameterContext context) {
        if (!supports(context)) {
            return CorrectionResult.noCorrection(context.originalValue());
        }

        Object value = context.originalValue();
        Class<?> targetType = context.parameterType();
        List<String> corrections = new ArrayList<>();

        try {
            // 1. 空值检查
            if (value == null) {
                if (isPrimitiveType(targetType)) {
                    return CorrectionResult.failed(null, "基本类型不能为null");
                }
                return CorrectionResult.noCorrection(null);
            }

            // 2. 类型兼容性检查
            if (isTypeCompatible(value, targetType)) {
                return CorrectionResult.noCorrection(value);
            }

            // 3. 尝试类型转换
            Object convertedValue = attemptTypeConversion(value, targetType, corrections);

            if (convertedValue != null) {
                double confidence = calculateTypeConversionConfidence(value, convertedValue, corrections);
                return CorrectionResult.success(convertedValue, value.toString(), corrections, confidence);
            }

            return CorrectionResult.failed(value.toString(),
                    String.format("无法将 %s 类型转换为 %s", value.getClass().getSimpleName(), targetType.getSimpleName()));

        } catch (Exception e) {
            logger.warn("类型验证失败: value={}, targetType={}", value, targetType, e);
            return CorrectionResult.failed(value.toString(), "类型验证异常: " + e.getMessage());
        }
    }

    @Override
    public boolean supports(ParameterContext context) {
        // 支持所有基本类型和常见对象类型的验证
        return true;
    }

    @Override
    public int getPriority() {
        return 60; // 在实体解析之后执行
    }

    /**
     * 检查类型兼容性
     */
    private boolean isTypeCompatible(Object value, Class<?> targetType) {
        Class<?> valueType = value.getClass();

        // 完全匹配
        if (targetType.isAssignableFrom(valueType)) {
            return true;
        }

        // 基本类型和包装类型匹配
        if (isPrimitiveTypeMatch(valueType, targetType)) {
            return true;
        }

        // 数值类型之间的兼容性
        if (isNumericTypeCompatible(valueType, targetType)) {
            return true;
        }

        return false;
    }

    /**
     * 尝试类型转换
     */
    private Object attemptTypeConversion(Object value, Class<?> targetType, List<String> corrections) {
        String stringValue = value.toString();

        try {
            // 字符串转换
            if (String.class.equals(targetType)) {
                if (!(value instanceof String)) {
                    corrections.add("转换为字符串类型");
                    return stringValue;
                }
                return value;
            }

            // 布尔类型转换
            if (Boolean.class.equals(targetType) || boolean.class.equals(targetType)) {
                return convertToBoolean(stringValue, corrections);
            }

            // 数值类型转换
            if (isNumericType(targetType)) {
                return convertToNumber(stringValue, targetType, corrections);
            }

            // 日期时间类型转换
            if (isDateTimeType(targetType)) {
                return convertToDateTime(stringValue, targetType, corrections);
            }

            // 枚举类型转换
            if (targetType.isEnum()) {
                return convertToEnum(stringValue, targetType, corrections);
            }

        } catch (Exception e) {
            logger.debug("类型转换失败: {} -> {}", value, targetType, e);
        }

        return null;
    }

    /**
     * 转换为布尔类型
     */
    private Boolean convertToBoolean(String value, List<String> corrections) {
        String lowerValue = value.toLowerCase().trim();

        if ("true".equals(lowerValue) || "1".equals(lowerValue) || "yes".equals(lowerValue) ||
                "是".equals(lowerValue) || "对".equals(lowerValue)) {
            corrections.add("转换为布尔类型");
            return true;
        }

        if ("false".equals(lowerValue) || "0".equals(lowerValue) || "no".equals(lowerValue) ||
                "否".equals(lowerValue) || "错".equals(lowerValue)) {
            corrections.add("转换为布尔类型");
            return false;
        }

        return null;
    }

    /**
     * 转换为数值类型
     */
    private Number convertToNumber(String value, Class<?> targetType, List<String> corrections) {
        try {
            // 清理数值字符串
            String cleanValue = value.replaceAll("[,\\s]", "");

            if (Integer.class.equals(targetType) || int.class.equals(targetType)) {
                corrections.add("转换为整数类型");
                return Integer.parseInt(cleanValue);
            } else if (Long.class.equals(targetType) || long.class.equals(targetType)) {
                corrections.add("转换为长整数类型");
                return Long.parseLong(cleanValue);
            } else if (Double.class.equals(targetType) || double.class.equals(targetType)) {
                corrections.add("转换为双精度浮点类型");
                return Double.parseDouble(cleanValue);
            } else if (Float.class.equals(targetType) || float.class.equals(targetType)) {
                corrections.add("转换为单精度浮点类型");
                return Float.parseFloat(cleanValue);
            } else if (BigDecimal.class.equals(targetType)) {
                corrections.add("转换为大数类型");
                return new BigDecimal(cleanValue);
            }
        } catch (NumberFormatException e) {
            logger.debug("数值转换失败: {}", value, e);
        }

        return null;
    }

    /**
     * 转换为日期时间类型
     */
    private Object convertToDateTime(String value, Class<?> targetType, List<String> corrections) {
        // 这里可以集成DateNormalizer的逻辑
        // 为了简化，这里只做基本的ISO格式解析
        try {
            if (LocalDate.class.equals(targetType)) {
                corrections.add("转换为日期类型");
                return LocalDate.parse(value);
            } else if (LocalDateTime.class.equals(targetType)) {
                corrections.add("转换为日期时间类型");
                return LocalDateTime.parse(value);
            } else if (LocalTime.class.equals(targetType)) {
                corrections.add("转换为时间类型");
                return LocalTime.parse(value);
            }
        } catch (Exception e) {
            logger.debug("日期时间转换失败: {}", value, e);
        }

        return null;
    }

    /**
     * 转换为枚举类型
     */
    private Object convertToEnum(String value, Class<?> enumType, List<String> corrections) {
        try {
            Object[] enumConstants = enumType.getEnumConstants();

            // 精确匹配
            for (Object enumConstant : enumConstants) {
                if (enumConstant.toString().equalsIgnoreCase(value.trim())) {
                    corrections.add("转换为枚举类型");
                    return enumConstant;
                }
            }

            // 部分匹配
            for (Object enumConstant : enumConstants) {
                if (enumConstant.toString().toLowerCase().contains(value.toLowerCase().trim())) {
                    corrections.add("转换为枚举类型（部分匹配）");
                    return enumConstant;
                }
            }
        } catch (Exception e) {
            logger.debug("枚举转换失败: {}", value, e);
        }

        return null;
    }

    /**
     * 检查是否为基本类型
     */
    private boolean isPrimitiveType(Class<?> type) {
        return type.isPrimitive();
    }

    /**
     * 检查基本类型匹配
     */
    private boolean isPrimitiveTypeMatch(Class<?> valueType, Class<?> targetType) {
        if (Integer.class.equals(valueType) && int.class.equals(targetType)) return true;
        if (Long.class.equals(valueType) && long.class.equals(targetType)) return true;
        if (Double.class.equals(valueType) && double.class.equals(targetType)) return true;
        if (Float.class.equals(valueType) && float.class.equals(targetType)) return true;
        if (Boolean.class.equals(valueType) && boolean.class.equals(targetType)) return true;
        if (Character.class.equals(valueType) && char.class.equals(targetType)) return true;
        if (Byte.class.equals(valueType) && byte.class.equals(targetType)) return true;
        if (Short.class.equals(valueType) && short.class.equals(targetType)) return true;

        return false;
    }

    /**
     * 检查数值类型兼容性
     */
    private boolean isNumericTypeCompatible(Class<?> valueType, Class<?> targetType) {
        return isNumericType(valueType) && isNumericType(targetType);
    }

    /**
     * 检查是否为数值类型
     */
    private boolean isNumericType(Class<?> type) {
        return Number.class.isAssignableFrom(type) ||
                type.equals(int.class) || type.equals(long.class) ||
                type.equals(double.class) || type.equals(float.class) ||
                type.equals(byte.class) || type.equals(short.class);
    }

    /**
     * 检查是否为日期时间类型
     */
    private boolean isDateTimeType(Class<?> type) {
        return LocalDate.class.equals(type) ||
                LocalDateTime.class.equals(type) ||
                LocalTime.class.equals(type);
    }

    /**
     * 计算类型转换置信度
     */
    private double calculateTypeConversionConfidence(Object original, Object converted, List<String> corrections) {
        double confidence = 0.8; // 基础置信度

        // 根据转换复杂度调整置信度
        for (String correction : corrections) {
            if (correction.contains("部分匹配")) {
                confidence -= 0.2;
            } else if (correction.contains("转换")) {
                confidence -= 0.1;
            }
        }

        return Math.max(0.5, confidence);
    }
}