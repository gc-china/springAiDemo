package org.zerolg.aidemo2.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.zerolg.aidemo2.audit.entity.PerformanceMetricsEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 性能指标Mapper
 */
@Mapper
public interface PerformanceMetricsMapper extends BaseMapper<PerformanceMetricsEntity> {

    /**
     * 根据执行ID查询性能指标
     */
    @Select("SELECT * FROM performance_metrics WHERE execution_id = #{executionId}")
    PerformanceMetricsEntity selectByExecutionId(@Param("executionId") String executionId);

    /**
     * 查询性能统计信息
     */
    @Select("SELECT " +
            "AVG(execution_time_ms) as avg_execution_time, " +
            "MIN(execution_time_ms) as min_execution_time, " +
            "MAX(execution_time_ms) as max_execution_time, " +
            "AVG(parameter_correction_time_ms) as avg_correction_time, " +
            "AVG(parameter_transformations) as avg_transformations, " +
            "COUNT(CASE WHEN cache_hit = true THEN 1 END) * 100.0 / COUNT(*) as cache_hit_rate " +
            "FROM performance_metrics WHERE created_at >= #{startTime}")
    Map<String, Object> getPerformanceStatistics(@Param("startTime") LocalDateTime startTime);

    /**
     * 查询慢执行记录
     */
    @Select("SELECT * FROM performance_metrics WHERE execution_time_ms > #{threshold} ORDER BY execution_time_ms DESC LIMIT #{limit}")
    List<PerformanceMetricsEntity> selectSlowExecutions(@Param("threshold") long threshold,
                                                        @Param("limit") int limit);
}