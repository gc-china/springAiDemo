package org.zerolg.aidemo2.correction.model;

import java.util.List;
import java.util.Map;

/**
 * 参数修正结果
 */
public record CorrectionResult(
        Object correctedValue,           // 修正后的值
        CorrectionStatus status,         // 修正状态
        String originalValue,            // 原始值
        List<String> corrections,        // 应用的修正操作
        Map<String, Object> metadata,   // 元数据信息
        double confidence               // 修正置信度 (0.0-1.0)
) {

    /**
     * 创建成功的修正结果
     */
    public static CorrectionResult success(Object correctedValue, String originalValue, List<String> corrections, double confidence) {
        return new CorrectionResult(correctedValue, CorrectionStatus.SUCCESS, originalValue, corrections, Map.of(), confidence);
    }

    /**
     * 创建需要确认的修正结果
     */
    public static CorrectionResult needsConfirmation(Object correctedValue, String originalValue, List<String> corrections, List<Object> candidates) {
        return new CorrectionResult(correctedValue, CorrectionStatus.NEEDS_CONFIRMATION, originalValue, corrections,
                Map.of("candidates", candidates), 0.7);
    }

    /**
     * 创建失败的修正结果
     */
    public static CorrectionResult failed(String originalValue, String reason) {
        return new CorrectionResult(originalValue, CorrectionStatus.FAILED, originalValue, List.of(),
                Map.of("reason", reason), 0.0);
    }

    /**
     * 创建无需修正的结果
     */
    public static CorrectionResult noCorrection(Object value) {
        return new CorrectionResult(value, CorrectionStatus.NO_CORRECTION_NEEDED, value.toString(), List.of(), Map.of(), 1.0);
    }
}