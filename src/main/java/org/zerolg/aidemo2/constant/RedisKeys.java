package org.zerolg.aidemo2.constant;

/**
 * Redis Key 定义常量
 */
public class RedisKeys {

    // Stream for document ingestion tasks
    public static final String STREAM_DOCUMENT_INGESTION = "document:ingestion:stream";

    // Stream for session events (e.g., chat messages)
    public static final String STREAM_SESSION_EVENT = "session:event:stream";

    // --- Session Memory Keys ---

    // ZSET: 会话心跳索引 (Score=Timestamp, Member=ConversationId)
    public static final String SESSION_HEARTBEAT = "aidemo:session:heartbeat";

    // List: 会话消息列表 (Key=aidemo:session:msg:{conversationId})
    public static final String SESSION_MSG_PREFIX = "aidemo:session:msg:";

    // Hash: 会话元数据 (Key=aidemo:session:meta:{conversationId}, Field=userId/startTime...)
    public static final String SESSION_META_PREFIX = "aidemo:session:meta:";
}