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

-- 删除可能存在的旧记录
DELETE
FROM flyway_schema_history
WHERE version = '1';

-- 插入版本1的记录，表示审计表已经存在
-- 使用当前V1__Create_audit_tables.sql的校验和
INSERT INTO flyway_schema_history (installed_rank, version, description, type, script, checksum,
                                   installed_by, installed_on, execution_time, success)
VALUES (1, '1', 'Create audit tables', 'SQL', 'V1__Create_audit_tables.sql',
        -619757082, 'manual', NOW(), 0, true)
ON CONFLICT
    (installed_rank)
    DO UPDATE SET
    version = EXCLUDED.version,
    description = EXCLUDED.description,
    type = EXCLUDED.type,
    script = EXCLUDED.script,
checksum = EXCLUDED.checksum,
    installed_by = EXCLUDED.installed_by,
    installed_on = EXCLUDED.installed_on,
    execution_time = EXCLUDED.execution_time,
    success = EXCLUDED.success;

-- 验证创建成功
SELECT *
FROM flyway_schema_history
ORDER BY installed_rank;