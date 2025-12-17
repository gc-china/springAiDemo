package org.zerolg.aidemo2.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import org.zerolg.aidemo2.handler.MapJsonTypeHandler;

import java.time.Instant;
import java.util.Map;

/**
 * 会话归档实体类
 * 
 * 映射数据库表: session_archives
 *
 * 作用：
 * 用于持久化存储会话事件。
 * 使用 MyBatis Plus 注解进行 ORM 映射。
 *
 * 特性：
 * 1. 使用 UUID 作为主键。
 * 2. payload 字段映射为 JSONB 类型，使用 JacksonTypeHandler 自动处理 JSON 转换。
 */
@TableName(value = "session_archives")
public class SessionArchive {

    /**
     * 主键 ID
     * 使用 UUID 生成策略 (或者由应用层生成)
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 会话 ID
     * 关联 Redis 中的 conversationId
     */
    private String conversationId;

    /**
     * 事件类型
     * 例如: MESSAGE_CREATED, SESSION_CLOSED
     */
    private String type;

    /**
     * 事件负载数据
     * 数据库中存储为 JSONB 类型
     * 使用 JacksonTypeHandler 自动将 Map 转换为 JSON 字符串存储，读取时自动转回 Map
     */
    @TableField(typeHandler = MapJsonTypeHandler.class)
    private Map<String, Object> payload;

    /**
     * 事件发生时间戳
     */
    private Instant timestamp;

    /**
     * 记录创建时间
     * 通常由数据库默认值处理，但也可以在应用层设置
     */
    private Instant createdAt;

    public SessionArchive() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
