package org.zerolg.aidemo2.model;

/**
 * 无支持内容处理策略
 */
public enum UnsupportedHandlingStrategy {
    /**
     * 标记警告 - 保留原内容，添加警告标识
     */
    MARK_WARNING("标记警告"),

    /**
     * 内容过滤 - 移除无支持的部分
     */
    FILTER_CONTENT("内容过滤"),

    /**
     * 重新生成 - 触发重新生成流程
     */
    REGENERATE("重新生成"),

    /**
     * 降级处理 - 标记为通用知识
     */
    DOWNGRADE("降级处理");

    private final String description;

    UnsupportedHandlingStrategy(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}