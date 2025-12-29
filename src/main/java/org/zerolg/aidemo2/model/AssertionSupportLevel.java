package org.zerolg.aidemo2.model;

/**
 * 断言支持度枚举
 */
public enum AssertionSupportLevel {
    /**
     * 完全支持 - 文档中有明确依据
     */
    FULLY_SUPPORTED("完全支持", "✅"),

    /**
     * 部分支持 - 文档中有部分依据
     */
    PARTIALLY_SUPPORTED("部分支持", "🟡"),

    /**
     * 无支持 - 文档中无依据
     */
    UNSUPPORTED("无支持", "⚠️"),

    /**
     * 矛盾 - 与文档内容矛盾
     */
    CONTRADICTED("矛盾", "❌");

    private final String description;
    private final String icon;

    AssertionSupportLevel(String description, String icon) {
        this.description = description;
        this.icon = icon;
    }

    public String getDescription() {
        return description;
    }

    public String getIcon() {
        return icon;
    }
}