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
     * 全文检索 (Full-Text Search)
     * 使用 PostgreSQL 的 plainto_tsquery 进行自然语言查询
     */
    @Select("""
                SELECT * FROM document_chunk
                WHERE content_search_vector @@ plainto_tsquery('simple', #{keyword})
                ORDER BY ts_rank(content_search_vector, plainto_tsquery('simple', #{keyword})) DESC
                LIMIT #{limit}
            """)
    List<DocumentChunk> searchByKeyword(@Param("keyword") String keyword, @Param("limit") int limit);

}
