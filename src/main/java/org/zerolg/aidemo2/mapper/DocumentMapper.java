package org.zerolg.aidemo2.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.zerolg.aidemo2.entity.Document;
import org.zerolg.aidemo2.entity.DocumentChunk;

import java.util.List;

@Mapper
public interface DocumentMapper extends BaseMapper<Document> {
}
