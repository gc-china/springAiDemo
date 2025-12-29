package org.zerolg.aidemo2.audit.model;

import java.util.Map;

/**
 * 性能指标
 */
public record PerformanceMetrics(
        long executionTimeMs,              // 执行时间（毫秒）
        long parameterCorrectionTimeMs,    // 参数修正时间（毫秒）
        int parameterTransformations,      // 参数转换次数
        boolean cacheHit,                  // 缓存命中
        Map<String, Object> customMetrics  // 自定义指标
) {
    public static PerformanceMetrics create(long executionTimeMs) {
        return new PerformanceMetrics(
                executionTimeMs,
                0L,
                0,
                false,
                Map.of()
        );
    }

    public PerformanceMetrics withParameterCorrection(long correctionTimeMs, int transformations) {
        return new PerformanceMetrics(
                executionTimeMs,
                correctionTimeMs,
                transformations,
                cacheHit,
                customMetrics
        );
    }

    public PerformanceMetrics withCacheHit(boolean cacheHit) {
        return new PerformanceMetrics(
                executionTimeMs,
                parameterCorrectionTimeMs,
                parameterTransformations,
                cacheHit,
                customMetrics
        );
    }

    public PerformanceMetrics withCustomMetrics(Map<String, Object> customMetrics) {
        return new PerformanceMetrics(
                executionTimeMs,
                parameterCorrectionTimeMs,
                parameterTransformations,
                cacheHit,
                customMetrics
        );
    }
}