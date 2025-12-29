package org.zerolg.aidemo2.audit.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 决策上下文实体
 */
@Data
@TableName("decision_context")
public class DecisionContextEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("execution_id")
    private String executionId;

    @TableField("session_id")
    private String sessionId;

    @TableField("tool_name")
    private String toolName;

    @TableField(value = "parameters", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> parameters;

    @TableField("decision")
    private String decision;

    @TableField("confidence")
    private BigDecimal confidence;

    @TableField(value = "alternatives", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> alternatives;

    @TableField(value = "context_factors", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> contextFactors;

    @TableField(value = "timestamp", fill = FieldFill.INSERT)
    private LocalDateTime timestamp;
}