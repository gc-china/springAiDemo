-- 1. 创建会话归档主表 (冷数据，存储完整内容)
CREATE TABLE IF NOT EXISTS session_archives
(
    conversation_id VARCHAR(64) PRIMARY KEY,             -- 与 Redis 中的 ID 保持一致
    user_id         VARCHAR(64) NOT NULL,
    content_json JSONB NOT NULL,                         -- 完整的对话历史，使用 JSONB 以便未来支持数据库级查询
    total_tokens    INT       DEFAULT 0,                 -- 总 Token 消耗
    archived_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP, -- 归档时间
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP  -- 创建时间
);

-- 为 user_id 创建索引，虽然主要通过 ID 查询，但有时也需要按用户清理冷数据
CREATE INDEX IF NOT EXISTS idx_session_archives_user_id ON session_archives(user_id);


-- 2. 创建会话归档索引表 (热数据，用于列表展示)
CREATE TABLE IF NOT EXISTS session_archive_index
(
    conversation_id  VARCHAR(64) PRIMARY KEY,
    user_id          VARCHAR(64) NOT NULL,
    summary          VARCHAR(512),        -- 会话摘要 (由 LLM 生成或截取)
    message_count    INT       DEFAULT 0, -- 消息条数
    total_tokens     INT       DEFAULT 0, -- Token 消耗
    start_time       TIMESTAMP,           -- 会话开始时间
    last_active_time TIMESTAMP,           -- 最后活跃时间
    archived_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 复合索引：通常查询场景是 "查某个用户的历史记录，按时间倒序"
CREATE INDEX IF NOT EXISTS idx_session_archive_index_user_time
    ON session_archive_index(user_id, last_active_time DESC);