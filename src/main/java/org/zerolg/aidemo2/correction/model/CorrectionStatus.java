package org.zerolg.aidemo2.correction.model;

/**
 * 参数修正状态
 */
public enum CorrectionStatus {
    /**
     * 修正成功
     */
    SUCCESS("修正成功"),

    /**
     * 需要用户确认
     */
    NEEDS_CONFIRMATION("需要确认"),

    /**
     * 修正失败
     */
    FAILED("修正失败"),

    /**
     * 无需修正
     */
    NO_CORRECTION_NEEDED("无需修正");

    private final String description;

    CorrectionStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}