package org.zerolg.aidemo2.model;

import java.time.Instant;
import java.util.Map;

/**
 * 会话事件模型
 * 
 * 作用：
 * 定义了发布到 Redis Stream 中的事件结构。
 * 所有的会话变更（如新消息、会话结束）都会被封装成这种统一的格式，
 * 确保下游消费者（如归档服务、审计服务）能够解析和处理。
 */
public class SessionEvent {

    // 事件唯一标识 ID (UUID)
    private String eventId;

    // 关联的会话 ID
    private String conversationId;

    // 事件类型 (例如: "MESSAGE_CREATED", "SESSION_CLOSED")
    private String type;

    // 事件负载数据 (通常包含消息内容的 JSON 结构)
    private Map<String, Object> payload;

    // 事件发生的时间戳
    private Instant timestamp;

    public SessionEvent() {
    }

    public SessionEvent(String eventId, String conversationId, String type, Map<String, Object> payload,
            Instant timestamp) {
        this.eventId = eventId;
        this.conversationId = conversationId;
        this.type = type;
        this.payload = payload;
        this.timestamp = timestamp;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
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
}
