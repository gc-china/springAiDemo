package org.zerolg.aidemo2.model;

import java.util.List;

/**
 * 详细验证结果（包含断言级别分析）
 */
public record DetailedVerificationResult(
        boolean passed,                           // 整体是否通过验证
        double confidence,                        // 整体置信度
        String reason,                           // 整体判断理由
        String correction,                       // 修正建议
        List<AssertionAnalysis> assertions,      // 断言级别分析
        UnsupportedHandlingResult handlingResult // 无支持内容处理结果
) {

    /**
     * 计算无支持内容比例
     */
    public double getUnsupportedRatio() {
        if (assertions == null || assertions.isEmpty()) {
            return 0.0;
        }

        long unsupportedCount = assertions.stream()
                .mapToLong(a -> a.supportLevel() == AssertionSupportLevel.UNSUPPORTED ? 1 : 0)
                .sum();

        return (double) unsupportedCount / assertions.size();
    }

    /**
     * 获取支持的断言数量
     */
    public long getSupportedCount() {
        if (assertions == null) return 0;
        return assertions.stream()
                .mapToLong(a -> a.supportLevel() == AssertionSupportLevel.FULLY_SUPPORTED ||
                        a.supportLevel() == AssertionSupportLevel.PARTIALLY_SUPPORTED ? 1 : 0)
                .sum();
    }

    /**
     * 获取无支持的断言数量
     */
    public long getUnsupportedCount() {
        if (assertions == null) return 0;
        return assertions.stream()
                .mapToLong(a -> a.supportLevel() == AssertionSupportLevel.UNSUPPORTED ? 1 : 0)
                .sum();
    }
}