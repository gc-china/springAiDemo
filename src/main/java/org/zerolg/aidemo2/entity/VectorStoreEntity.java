package org.zerolg.aidemo2.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;


import java.util.Map;
import java.util.UUID;

/**
 * Spring AI 默认向量存储表实体
 * 对应表名: vector_store
 * 用途: 用于直接操作向量表，例如去重检查或一致性校验
 */
@Data
@TableName(value = "vector_store", autoResultMap = true)
public class VectorStoreEntity {

    // 显式指定 TypeHandler 以解决 No typehandler found 错误

    private String id;

    private String content;

    // Spring AI 将 metadata 存储在 JSONB 类型的 metadata 字段中
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadata;

    // embedding 字段通常不需要在 MyBatis 中操作，除非有特殊需求
    // private String embedding; 
}