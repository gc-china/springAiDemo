-- 手动创建Flyway schema history表
-- 这将允许Flyway在现有数据库上工作

-- 创建Flyway schema history表
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

-- 创建索引
CREATE INDEX IF NOT EXISTS flyway_schema_history_s_idx ON flyway_schema_history (success);

-- 插入基线记录（表示当前状态为版本0）
INSERT INTO flyway_schema_history (installed_rank, version, description, type, script, checksum,
                                   installed_by, installed_on, execution_time, success)
VALUES (1, '0', '<< Flyway Baseline >>', 'BASELINE', '<< Flyway Baseline >>',
        NULL, 'manual', NOW(), 0, true)
ON CONFLICT (installed_rank) DO NOTHING;

-- 验证创建成功
SELECT *
FROM flyway_schema_history;