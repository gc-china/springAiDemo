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
 * 文档切片实体
 * 对应数据库表: document_chunk
 */
@Data
@TableName(value = "document_chunk", autoResultMap = true)
public class DocumentChunk {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String documentId;

    private String content;

    // PGVector 的 vector 类型在 MyBatis 中通常作为 String 或特定对象处理
    // 这里暂时不映射 embedding 字段，因为通常通过 Spring AI VectorStore 操作向量
    // 或者需要自定义 TypeHandler
    // @TableField(typeHandler = VectorTypeHandler.class)
    // private List<Double> embedding;

    private Integer tokenCount;

    private Integer chunkIndex;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadata;

    private LocalDateTime createdAt;
}
