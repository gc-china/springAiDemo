package org.zerolg.aidemo2.model;

/**
 * 无支持内容处理结果
 */
public record UnsupportedHandlingResult(
        UnsupportedHandlingStrategy strategy,  // 使用的处理策略
        String originalContent,                // 原始内容
        String processedContent,               // 处理后内容
        String warningMessage,                 // 警告信息
        boolean triggerRegeneration           // 是否触发重新生成
) {

    /**
     * 创建标记警告的处理结果
     */
    public static UnsupportedHandlingResult markWarning(String originalContent, String warningMessage) {
        return new UnsupportedHandlingResult(
                UnsupportedHandlingStrategy.MARK_WARNING,
                originalContent,
                originalContent,
                warningMessage,
                false
        );
    }

    /**
     * 创建内容过滤的处理结果
     */
    public static UnsupportedHandlingResult filterContent(String originalContent, String filteredContent) {
        return new UnsupportedHandlingResult(
                UnsupportedHandlingStrategy.FILTER_CONTENT,
                originalContent,
                filteredContent,
                "已过滤无支持依据的内容",
                false
        );
    }

    /**
     * 创建重新生成的处理结果
     */
    public static UnsupportedHandlingResult triggerRegeneration(String originalContent) {
        return new UnsupportedHandlingResult(
                UnsupportedHandlingStrategy.REGENERATE,
                originalContent,
                null,
                "内容质量不符合要求，建议重新生成",
                true
        );
    }

    /**
     * 创建降级处理的处理结果
     */
    public static UnsupportedHandlingResult downgrade(String originalContent) {
        return new UnsupportedHandlingResult(
                UnsupportedHandlingStrategy.DOWNGRADE,
                originalContent,
                originalContent,
                "回答基于通用知识，请谨慎参考",
                false
        );
    }
}