package org.zerolg.aidemo2.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.zerolg.aidemo2.entity.DocumentChunk;

import java.util.List;

@Mapper
public interface DocumentChunkMapper extends BaseMapper<DocumentChunk> {

    /**
     * 批量检查 chunk_hash 是否存在
     * 利用 PostgreSQL 的 JSONB 操作符 ->> 提取字段
     *
     * @param hashes 待检查的哈希列表
     * @return 已存在的哈希列表
     */
    @Select("<script>" +
            "SELECT metadata->>'chunk_hash' FROM document_chunk " +
            "WHERE metadata->>'chunk_hash' IN " +
            "<foreach item='hash' collection='hashes' open='(' separator=',' close=')'>" +
            "#{hash}" +
            "</foreach>" +
            "</script>")
    List<String> selectExistingHashes(@Param("hashes") List<String> hashes);

    /**
     * 关键词全文检索 (基于 ILIKE 模糊匹配)
     *
     * @param query 查询关键词
     * @param limit 返回条数限制
     */
    @Select("SELECT * FROM document_chunk WHERE content ILIKE CONCAT('%', #{query}, '%') LIMIT #{limit}")
    List<DocumentChunk> searchByKeyword(@Param("query") String query, @Param("limit") int limit);
}