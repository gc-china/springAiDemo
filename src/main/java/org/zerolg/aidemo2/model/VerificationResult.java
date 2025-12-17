package org.zerolg.aidemo2.model;

/**
 * 幻觉验证结果模型
 */
public record VerificationResult(
        boolean passed,          // 是否通过验证 (true=可信, false=存疑)
        double confidence,       // 裁判的置信度 (0.0 - 1.0)
        String reason,           // 判决理由 (例如: "文档中未提及粉色")
        String correction        // (可选) 建议的修正内容
) {
}