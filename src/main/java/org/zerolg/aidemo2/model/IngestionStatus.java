package org.zerolg.aidemo2.model;

/**
 * 文档摄入状态枚举
 */
public enum IngestionStatus {
    PENDING,    // 等待处理
    PROCESSING, // 处理中
    COMPLETED,  // 处理完成
    FAILED      // 处理失败
}
