package org.zerolg.aidemo2.audit.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 参数转换链实体
 */
@Data
@TableName("parameter_chain")
public class ParameterChainEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("execution_id")
    private String executionId;

    @TableField("parameter_name")
    private String parameterName;

    @TableField("original_value")
    private String originalValue;

    @TableField("transformed_value")
    private String transformedValue;

    @TableField("transformation_type")
    private String transformationType;

    @TableField("confidence")
    private BigDecimal confidence;

    @TableField("reason")
    private String reason;

    @TableField(value = "metadata", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadata;

    @TableField("step_order")
    private Integer stepOrder;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}