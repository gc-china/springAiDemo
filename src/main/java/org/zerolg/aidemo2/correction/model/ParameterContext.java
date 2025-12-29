package org.zerolg.aidemo2.correction.model;

import java.lang.reflect.Parameter;
import java.util.Map;

/**
 * 参数上下文信息
 */
public record ParameterContext(
        String parameterName,           // 参数名称
        Class<?> parameterType,         // 参数类型
        Object originalValue,           // 原始值
        Parameter parameter,            // 反射参数信息
        String methodName,              // 方法名称
        Map<String, Object> metadata   // 额外元数据
) {

    /**
     * 创建参数上下文
     */
    public static ParameterContext create(String parameterName, Class<?> parameterType, Object originalValue,
                                          Parameter parameter, String methodName) {
        return new ParameterContext(parameterName, parameterType, originalValue, parameter, methodName, Map.of());
    }

    /**
     * 添加元数据
     */
    public ParameterContext withMetadata(String key, Object value) {
        Map<String, Object> newMetadata = Map.of(key, value);
        return new ParameterContext(parameterName, parameterType, originalValue, parameter, methodName, newMetadata);
    }

    /**
     * 获取参数的字符串表示
     */
    public String getValueAsString() {
        return originalValue != null ? originalValue.toString() : "";
    }

    /**
     * 检查是否为字符串类型
     */
    public boolean isStringType() {
        return String.class.equals(parameterType);
    }

    /**
     * 检查是否为数值类型
     */
    public boolean isNumericType() {
        return Number.class.isAssignableFrom(parameterType) ||
                parameterType.equals(int.class) ||
                parameterType.equals(long.class) ||
                parameterType.equals(double.class) ||
                parameterType.equals(float.class);
    }
}