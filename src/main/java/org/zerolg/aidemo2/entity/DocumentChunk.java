package org.zerolg.aidemo2.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 向量切片实体
 * 对应关系型数据库表: document_chunk
 * 用于后台管理展示和基于 SQL 的全文检索
 */
@Data
@TableName(value = "document_chunk", autoResultMap = true)
public class DocumentChunk {

    /**
     * 主键 ID (与 vector_store 中的 ID 保持一致)
     */
    @TableId(type = IdType.INPUT)
    private String id;

    /**
     * 所属文档 ID
     */
    private String documentId;

    /**
     * 切片序号
     */
    private Integer chunkIndex;

    private String content;

    private Integer tokenCount;

    private LocalDateTime createdAt;

    // 存储额外元数据
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadata;
}