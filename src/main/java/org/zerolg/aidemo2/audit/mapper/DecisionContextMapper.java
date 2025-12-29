package org.zerolg.aidemo2.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.zerolg.aidemo2.audit.entity.DecisionContextEntity;

import java.util.List;

/**
 * 决策上下文Mapper
 */
@Mapper
public interface DecisionContextMapper extends BaseMapper<DecisionContextEntity> {

    /**
     * 根据会话ID查询决策上下文
     */
    @Select("SELECT * FROM decision_context WHERE session_id = #{sessionId} ORDER BY timestamp DESC LIMIT #{limit}")
    List<DecisionContextEntity> selectBySessionId(@Param("sessionId") String sessionId,
                                                  @Param("limit") int limit);

    /**
     * 根据工具名称查询决策上下文
     */
    @Select("SELECT * FROM decision_context WHERE tool_name = #{toolName} ORDER BY timestamp DESC LIMIT #{limit}")
    List<DecisionContextEntity> selectByToolName(@Param("toolName") String toolName,
                                                 @Param("limit") int limit);

    /**
     * 根据执行ID查询决策上下文
     */
    @Select("SELECT * FROM decision_context WHERE execution_id = #{executionId}")
    DecisionContextEntity selectByExecutionId(@Param("executionId") String executionId);
}