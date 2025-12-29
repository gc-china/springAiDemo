package org.zerolg.aidemo2.model;

/**
 * 单个断言分析结果
 */
public record AssertionAnalysis(
        String assertion,                    // 断言内容
        AssertionSupportLevel supportLevel, // 支持度级别
        String evidence,                     // 支持依据（来自文档的具体内容）
        String sourceLocation,               // 来源位置（如"文档第1段"）
        double confidence                    // 判断置信度 (0.0-1.0)
) {

    /**
     * 创建完全支持的断言分析
     */
    public static AssertionAnalysis fullySupported(String assertion, String evidence, String sourceLocation, double confidence) {
        return new AssertionAnalysis(assertion, AssertionSupportLevel.FULLY_SUPPORTED, evidence, sourceLocation, confidence);
    }

    /**
     * 创建部分支持的断言分析
     */
    public static AssertionAnalysis partiallySupported(String assertion, String evidence, String sourceLocation, double confidence) {
        return new AssertionAnalysis(assertion, AssertionSupportLevel.PARTIALLY_SUPPORTED, evidence, sourceLocation, confidence);
    }

    /**
     * 创建无支持的断言分析
     */
    public static AssertionAnalysis unsupported(String assertion, double confidence) {
        return new AssertionAnalysis(assertion, AssertionSupportLevel.UNSUPPORTED, null, null, confidence);
    }

    /**
     * 创建矛盾的断言分析
     */
    public static AssertionAnalysis contradicted(String assertion, String evidence, String sourceLocation, double confidence) {
        return new AssertionAnalysis(assertion, AssertionSupportLevel.CONTRADICTED, evidence, sourceLocation, confidence);
    }
}