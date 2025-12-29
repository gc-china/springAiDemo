package org.zerolg.aidemo2.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.zerolg.aidemo2.audit.entity.ToolExecutionAuditEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 工具执行审计Mapper
 */
@Mapper
public interface ToolExecutionAuditMapper extends BaseMapper<ToolExecutionAuditEntity> {

    /**
     * 根据会话ID查询审计记录
     */
    @Select("SELECT * FROM tool_execution_audit WHERE session_id = #{sessionId} ORDER BY start_time DESC LIMIT #{limit} OFFSET #{offset}")
    List<ToolExecutionAuditEntity> selectBySessionId(@Param("sessionId") String sessionId,
                                                     @Param("limit") int limit,
                                                     @Param("offset") int offset);

    /**
     * 根据工具名称查询审计记录
     */
    @Select("SELECT * FROM tool_execution_audit WHERE tool_name = #{toolName} ORDER BY start_time DESC LIMIT #{limit} OFFSET #{offset}")
    List<ToolExecutionAuditEntity> selectByToolName(@Param("toolName") String toolName,
                                                    @Param("limit") int limit,
                                                    @Param("offset") int offset);

    /**
     * 根据时间范围查询审计记录
     */
    @Select("SELECT * FROM tool_execution_audit WHERE start_time BETWEEN #{startTime} AND #{endTime} ORDER BY start_time DESC LIMIT #{limit} OFFSET #{offset}")
    List<ToolExecutionAuditEntity> selectByTimeRange(@Param("startTime") LocalDateTime startTime,
                                                     @Param("endTime") LocalDateTime endTime,
                                                     @Param("limit") int limit,
                                                     @Param("offset") int offset);

    /**
     * 获取会话统计信息
     */
    @Select("SELECT " +
            "COUNT(*) as total_executions, " +
            "COUNT(CASE WHEN status = 'ok' THEN 1 END) as successful_executions, " +
            "COUNT(CASE WHEN status = 'error' THEN 1 END) as failed_executions, " +
            "COUNT(CASE WHEN status = 'ambiguous' THEN 1 END) as ambiguous_executions, " +
            "AVG(execution_time_ms) as average_execution_time " +
            "FROM tool_execution_audit WHERE session_id = #{sessionId}")
    Map<String, Object> getSessionStatistics(@Param("sessionId") String sessionId);

    /**
     * 获取工具使用统计
     */
    @Select("SELECT tool_name, COUNT(*) as usage_count FROM tool_execution_audit " +
            "WHERE start_time >= #{startTime} GROUP BY tool_name ORDER BY usage_count DESC")
    List<Map<String, Object>> getToolUsageStatistics(@Param("startTime") LocalDateTime startTime);

    /**
     * 获取系统性能统计
     */
    @Select("SELECT " +
            "COUNT(*) as total_records, " +
            "AVG(execution_time_ms) as avg_execution_time, " +
            "MIN(execution_time_ms) as min_execution_time, " +
            "MAX(execution_time_ms) as max_execution_time, " +
            "COUNT(CASE WHEN status = 'ok' THEN 1 END) * 100.0 / COUNT(*) as success_rate " +
            "FROM tool_execution_audit WHERE start_time >= #{startTime}")
    Map<String, Object> getSystemStatistics(@Param("startTime") LocalDateTime startTime);
}