package org.zerolg.aidemo2.model;

import java.io.Serializable;

/**
 * 文档摄入任务模型
 * 用于在 Redis Stream 中传递任务信息
 */
public record IngestionTask(
    String ingestionId,
    String filePath,
    String fileName,
    String mimeType
) implements Serializable {
}
