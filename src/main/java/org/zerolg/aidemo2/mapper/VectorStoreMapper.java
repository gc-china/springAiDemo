package org.zerolg.aidemo2.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.zerolg.aidemo2.entity.VectorStoreEntity;

import java.util.List;

@Mapper
public interface VectorStoreMapper extends BaseMapper<VectorStoreEntity> {

    /**
     * 直接从 vector_store 表中检查 chunk_hash 是否存在
     * 这是去重的"最终事实来源"
     *
     * @param hashes 待检查的哈希列表
     * @return 已存在的哈希列表
     */
    @Select("<script>" +
            "SELECT metadata->>'chunk_hash' FROM vector_store " +
            "WHERE metadata->>'chunk_hash' IN " +
            "<foreach item='hash' collection='hashes' open='(' separator=',' close=')'>" +
            "#{hash}" +
            "</foreach>" +
            "</script>")
    List<String> selectExistingHashes(@Param("hashes") List<String> hashes);
}