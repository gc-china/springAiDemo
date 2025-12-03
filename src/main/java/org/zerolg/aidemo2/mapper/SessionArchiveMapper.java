package org.zerolg.aidemo2.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.zerolg.aidemo2.entity.SessionArchive;

/**
 * 会话归档 Mapper 接口
 * 
 * 作用：
 * 提供对 session_archives 表的数据库操作。
 * 继承 MyBatis Plus 的 BaseMapper，自动获得 CRUD 功能。
 * 
 * 无需编写 XML 文件，除非需要复杂的自定义 SQL。
 */
@Mapper
public interface SessionArchiveMapper extends BaseMapper<SessionArchive> {
    // 可以在这里添加自定义的查询方法，例如按时间范围查询等
}
