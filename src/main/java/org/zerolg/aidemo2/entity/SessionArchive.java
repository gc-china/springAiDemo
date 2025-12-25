package org.zerolg.aidemo2.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 会话归档主表实体 (冷数据)
 * 存储完整的对话内容，数据量大，仅在查看详情时加载。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "session_archives", autoResultMap = true)
public class SessionArchive {
    private String id;

    private String userId;

    /**
     * 会话 ID
     */
    private String conversationId;

    private Integer totalTokens;

    /**
     * 事件类型
     */
    private String type;

    /**
     * 事件载荷 (JSONB)
     * 使用 JacksonTypeHandler 自动完成 Map <-> JSON 转换
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> payload;

    /**
     * 事件发生时间
     */
    private Instant timestamp;

    /**
     * 记录创建时间
     */
    private Instant createdAt;

}