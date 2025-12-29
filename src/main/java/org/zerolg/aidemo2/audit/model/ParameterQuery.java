package org.zerolg.aidemo2.audit.model;

import java.time.Instant;

/**
 * 参数查询条件
 */
public record ParameterQuery(
        String toolName,            // 工具名称
        String parameterName,       // 参数名称
        String transformationType,  // 转换类型
        Instant startTime,          // 开始时间
        Instant endTime,            // 结束时间
        int limit,                  // 限制数量
        int offset                  // 偏移量
) {
    public static ParameterQuery forTool(String toolName) {
        return new ParameterQuery(toolName, null, null, null, null, 100, 0);
    }

    public static ParameterQuery forParameter(String parameterName) {
        return new ParameterQuery(null, parameterName, null, null, null, 100, 0);
    }

    public static ParameterQuery forTransformationType(String transformationType) {
        return new ParameterQuery(null, null, transformationType, null, null, 100, 0);
    }

    public ParameterQuery withLimit(int limit) {
        return new ParameterQuery(toolName, parameterName, transformationType, startTime, endTime, limit, offset);
    }

    public ParameterQuery withOffset(int offset) {
        return new ParameterQuery(toolName, parameterName, transformationType, startTime, endTime, limit, offset);
    }
}