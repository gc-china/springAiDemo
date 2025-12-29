package org.zerolg.aidemo2.audit.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 性能指标实体
 */
@Data
@TableName("performance_metrics")
public class PerformanceMetricsEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("execution_id")
    private String executionId;

    @TableField("execution_time_ms")
    private Long executionTimeMs;

    @TableField("parameter_correction_time_ms")
    private Long parameterCorrectionTimeMs;

    @TableField("parameter_transformations")
    private Integer parameterTransformations;

    @TableField("cache_hit")
    private Boolean cacheHit;

    @TableField(value = "custom_metrics", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> customMetrics;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}