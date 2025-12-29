-- Manual setup script for audit tables
-- Run this if you want to create audit tables manually without Flyway

-- Check if tables exist
SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_name IN ('tool_execution_audit', 'parameter_chain', 'decision_context', 'performance_metrics');

-- If the above query returns empty results, run the following:

-- 工具执行审计表
CREATE TABLE IF NOT EXISTS tool_execution_audit
(
    id BIGSERIAL PRIMARY KEY,
    execution_id      VARCHAR(100) UNIQUE NOT NULL,
    trace_id          VARCHAR(100),
    session_id        VARCHAR(100)        NOT NULL,
    user_id           VARCHAR(100),
    tool_name         VARCHAR(200)        NOT NULL,
    method_name       VARCHAR(200)        NOT NULL,
    original_params JSONB,
    final_params JSONB,
    status            VARCHAR(50)         NOT NULL,
    result JSONB,
    error_message     TEXT,
    start_time        TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time          TIMESTAMP WITH TIME ZONE,
    execution_time_ms BIGINT DEFAULT 0,
    context JSONB,
    created_at        TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 参数转换链表
CREATE TABLE IF NOT EXISTS parameter_chain
(
    id BIGSERIAL PRIMARY KEY,
    execution_id        VARCHAR(100) NOT NULL,
    parameter_name      VARCHAR(200) NOT NULL,
    original_value      TEXT,
    transformed_value   TEXT,
    transformation_type VARCHAR(100) NOT NULL,
    confidence          DECIMAL(5, 4) DEFAULT 1.0,
    reason              TEXT,
    metadata JSONB,
    step_order          INTEGER      NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 决策上下文表
CREATE TABLE IF NOT EXISTS decision_context
(
    id BIGSERIAL PRIMARY KEY,
    execution_id VARCHAR(100),
    session_id   VARCHAR(100) NOT NULL,
    tool_name    VARCHAR(200) NOT NULL,
    parameters JSONB,
    decision     VARCHAR(500) NOT NULL,
    confidence   DECIMAL(5, 4) DEFAULT 1.0,
    alternatives JSONB,
    context_factors JSONB,
    timestamp    TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 性能指标表
CREATE TABLE IF NOT EXISTS performance_metrics
(
    id BIGSERIAL PRIMARY KEY,
    execution_id                 VARCHAR(100) NOT NULL,
    execution_time_ms            BIGINT       NOT NULL,
    parameter_correction_time_ms BIGINT  DEFAULT 0,
    parameter_transformations    INTEGER DEFAULT 0,
    cache_hit                    BOOLEAN DEFAULT FALSE,
    custom_metrics JSONB,
    created_at                   TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引优化查询性能
CREATE INDEX IF NOT EXISTS idx_audit_session_id ON tool_execution_audit(session_id);
CREATE INDEX IF NOT EXISTS idx_audit_tool_name ON tool_execution_audit(tool_name);
CREATE INDEX IF NOT EXISTS idx_audit_user_id ON tool_execution_audit(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_start_time ON tool_execution_audit(start_time);
CREATE INDEX IF NOT EXISTS idx_audit_status ON tool_execution_audit(status);

CREATE INDEX IF NOT EXISTS idx_param_chain_execution_id ON parameter_chain(execution_id);
CREATE INDEX IF NOT EXISTS idx_param_chain_type ON parameter_chain(transformation_type);

CREATE INDEX IF NOT EXISTS idx_decision_session_id ON decision_context(session_id);
CREATE INDEX IF NOT EXISTS idx_decision_tool_name ON decision_context(tool_name);

CREATE INDEX IF NOT EXISTS idx_metrics_execution_id ON performance_metrics(execution_id);

-- 创建更新时间触发器
CREATE
OR
REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN NEW.updated_at = CURRENT_TIMESTAMP;
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS update_audit_updated_at ON tool_execution_audit;
CREATE TRIGGER update_audit_updated_at
    BEFORE UPDATE
    ON tool_execution_audit
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Verify tables were created
SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_name IN ('tool_execution_audit', 'parameter_chain', 'decision_context', 'performance_metrics');