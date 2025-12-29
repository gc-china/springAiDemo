-- 修复Flyway基线问题的脚本
-- 这个脚本将创建Flyway历史表并标记当前状态为已迁移

-- 1. 创建Flyway schema history表
CREATE TABLE IF NOT EXISTS flyway_schema_history
(
    installed_rank INTEGER       NOT NULL,
    version        VARCHAR(50),
    description    VARCHAR(200)  NOT NULL,
    type           VARCHAR(20)   NOT NULL,
    script         VARCHAR(1000) NOT NULL,
    checksum       INTEGER,
    installed_by   VARCHAR(100)  NOT NULL,
    installed_on   TIMESTAMP     NOT NULL DEFAULT NOW(),
    execution_time INTEGER       NOT NULL,
    success        BOOLEAN       NOT NULL,
    CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank)
);

-- 2. 创建索引
CREATE INDEX IF NOT EXISTS flyway_schema_history_s_idx ON flyway_schema_history (success);

-- 3. 插入基线记录，标记V1已经执行过了
INSERT INTO flyway_schema_history (installed_rank, version, description, type, script, checksum,
                                   installed_by, installed_on, execution_time, success)
VALUES (1, '1', 'Create audit tables', 'SQL', 'V1__Create_audit_tables.sql',
        -1234567890, 'manual_baseline', NOW(), 0, true)
ON CONFLICT (installed_rank) DO NOTHING;

-- 4. 验证
SELECT *
FROM flyway_schema_history;

-- 5. 检查现有表
SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_name IN ('tool_execution_audit', 'parameter_chain', 'decision_context', 'performance_metrics');