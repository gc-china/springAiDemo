package org.zerolg.aidemo2.audit.model;

import java.util.Map;

/**
 * 参数转换模式分析结果
 */
public record ParameterPatternAnalysis(
        String toolName,                        // 工具名称
        int totalTransformations,               // 总转换次数
        Map<String, Integer> transformationTypes, // 转换类型统计
        Map<String, Double> averageConfidence,  // 平均置信度
        Map<String, Integer> parameterFrequency, // 参数频率
        double overallSuccessRate,              // 整体成功率
        Map<String, String> commonPatterns      // 常见模式
) {
    public static ParameterPatternAnalysis empty(String toolName) {
        return new ParameterPatternAnalysis(
                toolName,
                0,
                Map.of(),
                Map.of(),
                Map.of(),
                0.0,
                Map.of()
        );
    }
}