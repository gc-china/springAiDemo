-- 会话归档表创建脚本
-- 用于存储从 Redis 归档的会话事件数据

CREATE TABLE IF NOT EXISTS session_archives (
    -- 主键 ID (UUID 字符串)
    id VARCHAR(36) PRIMARY KEY,
    
    -- 会话 ID (关联 Redis 中的 conversationId)
    conversation_id VARCHAR(255) NOT NULL,
    
    -- 事件类型 (例如: MESSAGE_CREATED, SESSION_CLOSED)
    type VARCHAR(50) NOT NULL,
    
    -- 事件负载 (JSON 格式存储具体数据，如消息内容)
    -- 使用 JSONB 类型以支持高效查询和索引
    payload JSONB NOT NULL,
    
    -- 事件发生时间戳
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    
    -- 记录创建时间 (入库时间)
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 为 conversation_id 创建索引，加速按会话查询
CREATE INDEX IF NOT EXISTS idx_session_archives_conversation_id ON session_archives(conversation_id);

-- 为 timestamp 创建索引，加速按时间范围查询
CREATE INDEX IF NOT EXISTS idx_session_archives_timestamp ON session_archives(timestamp);

COMMENT ON TABLE session_archives IS '会话归档表：存储历史会话事件';
COMMENT ON COLUMN session_archives.id IS '主键 ID';
COMMENT ON COLUMN session_archives.conversation_id IS '会话 ID';
COMMENT ON COLUMN session_archives.type IS '事件类型';
COMMENT ON COLUMN session_archives.payload IS '事件负载数据 (JSON)';
COMMENT ON COLUMN session_archives.timestamp IS '事件发生时间';
COMMENT ON COLUMN session_archives.created_at IS '记录入库时间';
