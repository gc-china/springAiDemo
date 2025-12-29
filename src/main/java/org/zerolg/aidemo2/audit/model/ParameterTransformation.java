package org.zerolg.aidemo2.audit.model;

import java.util.Map;

/**
 * 参数转换步骤
 */
public record ParameterTransformation(
        String parameterName,        // 参数名
        Object originalValue,        // 原始值
        Object transformedValue,     // 转换后值
        String transformationType,   // 转换类型
        double confidence,           // 置信度
        String reason,              // 转换原因
        Map<String, Object> metadata // 转换元数据
) {
    public static ParameterTransformation create(String parameterName, Object originalValue,
                                                 Object transformedValue, String transformationType,
                                                 double confidence, String reason) {
        return new ParameterTransformation(
                parameterName,
                originalValue,
                transformedValue,
                transformationType,
                confidence,
                reason,
                Map.of()
        );
    }

    public ParameterTransformation withMetadata(Map<String, Object> metadata) {
        return new ParameterTransformation(
                parameterName,
                originalValue,
                transformedValue,
                transformationType,
                confidence,
                reason,
                metadata
        );
    }
}