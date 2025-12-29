package org.zerolg.aidemo2.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.zerolg.aidemo2.audit.entity.ParameterChainEntity;

import java.util.List;

/**
 * 参数转换链Mapper
 */
@Mapper
public interface ParameterChainMapper extends BaseMapper<ParameterChainEntity> {

    /**
     * 根据执行ID查询参数转换链
     */
    @Select("SELECT * FROM parameter_chain WHERE execution_id = #{executionId} ORDER BY step_order ASC")
    List<ParameterChainEntity> selectByExecutionId(@Param("executionId") String executionId);

    /**
     * 根据转换类型查询参数转换记录
     */
    @Select("SELECT * FROM parameter_chain WHERE transformation_type = #{transformationType} ORDER BY created_at DESC LIMIT #{limit}")
    List<ParameterChainEntity> selectByTransformationType(@Param("transformationType") String transformationType,
                                                          @Param("limit") int limit);

    /**
     * 删除指定执行ID的所有参数转换记录
     */
    @Delete("DELETE FROM parameter_chain WHERE execution_id = #{executionId}")
    int deleteByExecutionId(@Param("executionId") String executionId);
}