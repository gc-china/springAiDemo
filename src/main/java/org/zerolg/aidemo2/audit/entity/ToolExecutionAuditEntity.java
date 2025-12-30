package org.zerolg.aidemo2.audit.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * 工具执行审计实体
 */
@Data
@TableName("tool_execution_audit")
public class ToolExecutionAuditEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("execution_id")
    private String executionId;

    @TableField("trace_id")
    private String traceId;

    @TableField("session_id")
    private String sessionId;

    @TableField("user_id")
    private String userId;

    @TableField("tool_name")
    private String toolName;

    @TableField("method_name")
    private String methodName;

    @TableField(value = "original_params", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> originalParams;

    @TableField(value = "final_params", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> finalParams;

    @TableField("status")
    private String status;

    @TableField(value = "result", typeHandler = JacksonTypeHandler.class)
    private Object result;

    @TableField("error_message")
    private String errorMessage;

    @TableField("start_time")
    private Instant startTime;

    @TableField("end_time")
    private Instant endTime;

    @TableField("execution_time_ms")
    private Long executionTimeMs;

    @TableField(value = "context", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> context;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Instant createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
}