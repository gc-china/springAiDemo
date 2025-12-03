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
 * 文档元数据实体
 * 对应数据库表: document
 */
@Data
@TableName(value = "document", autoResultMap = true)
public class Document {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String title;

    private String sourceUrl;

    private String filePath;

    private String mimeType;

    private Integer totalTokens;

    private Integer chunkCount;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadata;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Boolean isDeleted;
}
