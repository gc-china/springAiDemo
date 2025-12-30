-- ==================== Spring AI Demo 2 数据库初始化脚本 ====================
-- 此脚本用于创建AI演示项目所需的完整数据库表结构和扩展
-- 执行环境：PostgreSQL 16+ with PGVector extension
-- 创建时间：2024-12-30
-- 版本：v1.0

-- ==================== 启用PostgreSQL扩展 ====================

-- 启用vector扩展：提供向量数据类型和相似性搜索功能
-- 这是PGVector的核心扩展，支持高维向量存储和检索
CREATE EXTENSION IF NOT EXISTS vector;

-- 启用hstore扩展：提供键值对数据类型，用于灵活的元数据存储
CREATE EXTENSION IF NOT EXISTS hstore;

-- 启用uuid-ossp扩展：提供UUID生成函数，用于生成唯一标识符
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 启用btree_gin扩展：支持对JSONB字段创建GIN索引
CREATE
EXTENSION IF NOT EXISTS btree_gin;

-- ==================== 核心业务表 ====================

-- ==================== Spring AI向量存储表 ====================
-- 这是Spring AI框架默认使用的向量存储表
-- 用于存储文档向量和元数据，支持RAG（检索增强生成）功能
CREATE TABLE IF NOT EXISTS vector_store (
    -- 主键：使用UUID确保全局唯一性
                                            id UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    -- 文档内容：存储原始文本内容
                                            content TEXT,
    -- 元数据：存储文档的附加信息（JSON格式）
    -- 如文件名、来源、创建时间等
                                            metadata JSONB,
    -- 向量嵌入：存储文档的向量表示（1536维）
    -- 维度需要与嵌入模型的输出维度一致
                                            embedding VECTOR(1536) -- 根据使用的嵌入模型调整维度
);

-- 创建HNSW索引：用于高效的向量相似性搜索
-- HNSW (Hierarchical Navigable Small World) 是一种高性能的近似最近邻搜索算法
-- vector_cosine_ops 指定使用余弦距离进行相似性计算
CREATE INDEX IF NOT EXISTS idx_vector_store_embedding
    ON vector_store USING HNSW (embedding vector_cosine_ops);

-- 创建元数据GIN索引：支持对JSONB字段的高效查询
CREATE INDEX IF NOT EXISTS idx_vector_store_metadata
    ON vector_store USING GIN (metadata);

-- ==================== 文档管理表 ====================
-- 存储文档的详细元数据信息，与vector_store表配合使用
-- 提供更丰富的文档管理功能
CREATE TABLE IF NOT EXISTS document (
    -- 文档唯一标识符
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    -- 文档标题
    title VARCHAR(255) NOT NULL,
    -- 文档来源URL（如果是网络文档）
    source_url VARCHAR(1024),
    -- 文件路径（如果是本地文件）
    file_path VARCHAR(1024),
    -- MIME类型：标识文件格式（如application/pdf, text/plain等）
    mime_type VARCHAR(100),
    -- 总token数：用于成本计算和性能优化
    total_tokens INTEGER,
    -- 分块数量：文档被切分成多少个小块
    chunk_count INTEGER,
    -- 扩展元数据：存储其他自定义信息（JSONB格式，支持索引）
    metadata JSONB,
    -- 创建时间：记录文档首次上传时间
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    -- 更新时间：记录文档最后修改时间
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    -- 软删除标记：标记文档是否被删除（不物理删除，便于恢复）
    is_deleted BOOLEAN DEFAULT FALSE
);

-- 为document表创建索引
CREATE INDEX IF NOT EXISTS idx_document_title ON document(title);
CREATE INDEX IF NOT EXISTS idx_document_mime_type ON document(mime_type);
CREATE INDEX IF NOT EXISTS idx_document_created_at ON document(created_at);
CREATE INDEX IF NOT EXISTS idx_document_is_deleted ON document(is_deleted);
CREATE INDEX IF NOT EXISTS idx_document_metadata ON document USING GIN (metadata);

-- ==================== 文档分块表 ====================
-- 存储文档分块的内容和向量，这是RAG系统的核心数据表
-- 每个文档会被切分成多个小块，每个小块都有自己的向量表示
CREATE TABLE IF NOT EXISTS document_chunk (
    -- 分块唯一标识符（与vector_store表的id保持一致）
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    -- 关联的文档ID，建立外键关系
    document_id UUID REFERENCES document(id) ON DELETE CASCADE,
    -- 分块在文档中的序号（从0开始）
    chunk_index INTEGER NOT NULL,
    -- 分块的文本内容
    content TEXT NOT NULL,
    -- 分块的token数量
    token_count INTEGER,
    -- 分块的元数据信息
    metadata JSONB,
    -- 创建时间
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 为document_chunk表创建索引
CREATE INDEX IF NOT EXISTS idx_document_chunk_document_id ON document_chunk(document_id);
CREATE INDEX IF NOT EXISTS idx_document_chunk_chunk_index ON document_chunk(chunk_index);
CREATE INDEX IF NOT EXISTS idx_document_chunk_created_at ON document_chunk(created_at);
CREATE INDEX IF NOT EXISTS idx_document_chunk_metadata ON document_chunk USING GIN (metadata);

-- ==================== 文档文件记录表 ====================
-- 用于文件级别的去重检测，避免重复处理相同的文件
CREATE TABLE IF NOT EXISTS document_file
(
    -- 记录唯一标识符
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    -- 文件哈希值（MD5），用于去重检测
    file_hash   VARCHAR(32)  NOT NULL UNIQUE,
    -- 原始文件名
    filename    VARCHAR(255) NOT NULL,
    -- 处理状态：COMPLETED, FAILED, PROCESSING, PENDING
    status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    -- 记录创建时间
    create_time TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 为document_file表创建索引
CREATE INDEX IF NOT EXISTS idx_document_file_hash ON document_file(file_hash);
CREATE INDEX IF NOT EXISTS idx_document_file_status ON document_file(status);
CREATE INDEX IF NOT EXISTS idx_document_file_create_time ON document_file(create_time);

-- ==================== 会话管理表 ====================

-- ==================== 会话归档主表 ====================
-- 存储用户会话的历史记录，实现冷热数据分离
-- 当Redis中的会话数据过期后，会被归档到此表中
CREATE TABLE IF NOT EXISTS session_archives (
    -- 归档记录的唯一标识符
    id VARCHAR(36) PRIMARY KEY,
    -- 用户ID
    user_id      VARCHAR(255),
    -- 会话ID：标识哪个对话会话
    conversation_id VARCHAR(255) NOT NULL,
    -- 总token消耗
    total_tokens INTEGER,
    -- 记录类型：如message（消息）、metadata（元数据）等
    type VARCHAR(50) NOT NULL,
    -- 数据载荷：存储实际的会话数据（JSONB格式）
    payload JSONB NOT NULL,
    -- 原始时间戳：记录数据的原始创建时间
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    -- 归档时间：记录数据被归档的时间
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 为session_archives表创建索引
CREATE INDEX IF NOT EXISTS idx_session_archives_conversation_id ON session_archives(conversation_id);
CREATE INDEX IF NOT EXISTS idx_session_archives_user_id ON session_archives(user_id);
CREATE INDEX IF NOT EXISTS idx_session_archives_timestamp ON session_archives(timestamp);
CREATE INDEX IF NOT EXISTS idx_session_archives_type ON session_archives(type);
CREATE INDEX IF NOT EXISTS idx_session_archives_created_at ON session_archives(created_at);

-- ==================== 会话归档索引表 ====================
-- 提供轻量级的历史会话查询能力，避免直接扫描存储了大量JSON内容的主表
CREATE TABLE IF NOT EXISTS session_archive_index
(
    -- 会话ID作为主键
    conversation_id  VARCHAR(255) PRIMARY KEY,
    -- 用户ID
    user_id          VARCHAR(255),
    -- 会话摘要（可由LLM生成或截取第一句话）
    summary          TEXT,
    -- 消息总数
    message_count    INTEGER DEFAULT 0,
    -- 总Token消耗
    total_tokens     INTEGER DEFAULT 0,
    -- 会话开始时间
    start_time       TIMESTAMP WITHOUT TIME ZONE,
    -- 会话最后活跃时间（即归档触发时间）
    last_active_time TIMESTAMP WITHOUT TIME ZONE,
    -- 归档时间
    archived_at      TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 为session_archive_index表创建索引
CREATE INDEX IF NOT EXISTS idx_session_archive_index_user_id ON session_archive_index(user_id);
CREATE INDEX IF NOT EXISTS idx_session_archive_index_start_time ON session_archive_index(start_time);
CREATE INDEX IF NOT EXISTS idx_session_archive_index_archived_at ON session_archive_index(archived_at);

-- ==================== 审计监控表 ====================

-- ==================== 工具执行审计主表 ====================
-- 记录AI工具调用的完整审计信息
CREATE TABLE IF NOT EXISTS tool_execution_audit
(
    -- 自增主键
    id BIGSERIAL PRIMARY KEY,
    -- 执行唯一标识符
    execution_id      VARCHAR(36)  NOT NULL UNIQUE,
    -- 链路追踪ID
    trace_id          VARCHAR(36),
    -- 会话ID
    session_id        VARCHAR(255),
    -- 用户ID
    user_id           VARCHAR(255),
    -- 工具名称
    tool_name         VARCHAR(255) NOT NULL,
    -- 方法名称
    method_name       VARCHAR(255),
    -- 原始参数（JSON格式）
    original_params JSONB,
    -- 最终参数（JSON格式）
    final_params JSONB,
    -- 执行状态：SUCCESS, FAILED, AMBIGUOUS
    status            VARCHAR(20)  NOT NULL,
    -- 执行结果（JSON格式）
    result JSONB,
    -- 错误信息
    error_message     TEXT,
    -- 开始时间
    start_time        TIMESTAMP WITH TIME ZONE,
    -- 结束时间
    end_time          TIMESTAMP WITH TIME ZONE,
    -- 执行时间（毫秒）
    execution_time_ms BIGINT,
    -- 执行上下文（JSON格式）
    context JSONB,
    -- 创建时间
    created_at        TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    -- 更新时间
    updated_at        TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 为tool_execution_audit表创建索引
CREATE INDEX IF NOT EXISTS idx_tool_execution_audit_execution_id ON tool_execution_audit(execution_id);
CREATE INDEX IF NOT EXISTS idx_tool_execution_audit_trace_id ON tool_execution_audit(trace_id);
CREATE INDEX IF NOT EXISTS idx_tool_execution_audit_session_id ON tool_execution_audit(session_id);
CREATE INDEX IF NOT EXISTS idx_tool_execution_audit_user_id ON tool_execution_audit(user_id);
CREATE INDEX IF NOT EXISTS idx_tool_execution_audit_tool_name ON tool_execution_audit(tool_name);
CREATE INDEX IF NOT EXISTS idx_tool_execution_audit_status ON tool_execution_audit(status);
CREATE INDEX IF NOT EXISTS idx_tool_execution_audit_created_at ON tool_execution_audit(created_at);
CREATE INDEX IF NOT EXISTS idx_tool_execution_audit_start_time ON tool_execution_audit(start_time);

-- ==================== 参数转换链表 ====================
-- 记录参数在纠错过程中的完整转换链路
CREATE TABLE IF NOT EXISTS parameter_chain
(
    -- 自增主键
    id BIGSERIAL PRIMARY KEY,
    -- 关联的执行ID
    execution_id        VARCHAR(36)  NOT NULL,
    -- 参数名称
    parameter_name      VARCHAR(255) NOT NULL,
    -- 原始值
    original_value      TEXT,
    -- 转换后的值
    transformed_value   TEXT,
    -- 转换类型：NORMALIZATION, VALIDATION, LLM_CORRECTION, etc.
    transformation_type VARCHAR(50)  NOT NULL,
    -- 置信度（0.0-1.0）
    confidence          DECIMAL(3, 2),
    -- 转换原因
    reason              TEXT,
    -- 转换元数据（JSON格式）
    metadata JSONB,
    -- 转换步骤顺序
    step_order          INTEGER      NOT NULL,
    -- 创建时间
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    -- 外键约束
    FOREIGN KEY (execution_id) REFERENCES tool_execution_audit (execution_id) ON DELETE CASCADE
);

-- 为parameter_chain表创建索引
CREATE INDEX IF NOT EXISTS idx_parameter_chain_execution_id ON parameter_chain(execution_id);
CREATE INDEX IF NOT EXISTS idx_parameter_chain_parameter_name ON parameter_chain(parameter_name);
CREATE INDEX IF NOT EXISTS idx_parameter_chain_transformation_type ON parameter_chain(transformation_type);
CREATE INDEX IF NOT EXISTS idx_parameter_chain_step_order ON parameter_chain(step_order);
CREATE INDEX IF NOT EXISTS idx_parameter_chain_created_at ON parameter_chain(created_at);

-- ==================== 决策上下文表 ====================
-- 记录AI决策过程和推理依据
CREATE TABLE IF NOT EXISTS decision_context
(
    -- 自增主键
    id BIGSERIAL PRIMARY KEY,
    -- 关联的执行ID
    execution_id VARCHAR(36)  NOT NULL,
    -- 会话ID
    session_id   VARCHAR(255),
    -- 工具名称
    tool_name    VARCHAR(255) NOT NULL,
    -- 决策参数（JSON格式）
    parameters JSONB,
    -- 决策结果
    decision     TEXT         NOT NULL,
    -- 决策置信度（0.0-1.0）
    confidence   DECIMAL(3, 2),
    -- 备选方案（JSON格式）
    alternatives JSONB,
    -- 上下文因素（JSON格式）
    context_factors JSONB,
    -- 决策时间戳
    timestamp    TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    -- 外键约束
    FOREIGN KEY (execution_id) REFERENCES tool_execution_audit (execution_id) ON DELETE CASCADE
);

-- 为decision_context表创建索引
CREATE INDEX IF NOT EXISTS idx_decision_context_execution_id ON decision_context(execution_id);
CREATE INDEX IF NOT EXISTS idx_decision_context_session_id ON decision_context(session_id);
CREATE INDEX IF NOT EXISTS idx_decision_context_tool_name ON decision_context(tool_name);
CREATE INDEX IF NOT EXISTS idx_decision_context_timestamp ON decision_context(timestamp);

-- ==================== 性能指标表 ====================
-- 记录系统性能相关的指标数据
CREATE TABLE IF NOT EXISTS performance_metrics
(
    -- 自增主键
    id BIGSERIAL PRIMARY KEY,
    -- 关联的执行ID
    execution_id                 VARCHAR(36) NOT NULL,
    -- 总执行时间（毫秒）
    execution_time_ms            BIGINT,
    -- 参数纠错时间（毫秒）
    parameter_correction_time_ms BIGINT,
    -- 参数转换次数
    parameter_transformations    INTEGER DEFAULT 0,
    -- 是否命中缓存
    cache_hit                    BOOLEAN DEFAULT FALSE,
    -- 自定义指标（JSON格式）
    custom_metrics JSONB,
    -- 创建时间
    created_at                   TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    -- 外键约束
    FOREIGN KEY (execution_id) REFERENCES tool_execution_audit (execution_id) ON DELETE CASCADE
);

-- 为performance_metrics表创建索引
CREATE INDEX IF NOT EXISTS idx_performance_metrics_execution_id ON performance_metrics(execution_id);
CREATE INDEX IF NOT EXISTS idx_performance_metrics_execution_time ON performance_metrics(execution_time_ms);
CREATE INDEX IF NOT EXISTS idx_performance_metrics_cache_hit ON performance_metrics(cache_hit);
CREATE INDEX IF NOT EXISTS idx_performance_metrics_created_at ON performance_metrics(created_at);

-- ==================== 创建视图 ====================

-- ==================== 审计统计视图 ====================
-- 提供审计数据的统计查询视图
CREATE OR REPLACE VIEW audit_statistics AS
SELECT tool_name,
       COUNT(*)                                                                  as total_executions,
       COUNT(CASE WHEN status = 'SUCCESS' THEN 1 END)                            as successful_executions,
       COUNT(CASE WHEN status = 'FAILED' THEN 1 END)                             as failed_executions,
       COUNT(CASE WHEN status = 'AMBIGUOUS' THEN 1 END)                          as ambiguous_executions,
       ROUND(AVG(execution_time_ms), 2)                                          as avg_execution_time_ms,
       ROUND(PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY execution_time_ms), 2) as p95_execution_time_ms,
       DATE_TRUNC('day', created_at)                                             as date
FROM tool_execution_audit
GROUP BY tool_name, DATE_TRUNC('day', created_at)
ORDER BY date DESC, tool_name;

-- ==================== 会话统计视图 ====================
-- 提供会话数据的统计查询视图
CREATE OR REPLACE VIEW session_statistics AS
SELECT user_id,
       COUNT(DISTINCT conversation_id) as total_conversations,
       SUM(message_count)              as total_messages,
       SUM(total_tokens)               as total_tokens,
       AVG(message_count)              as avg_messages_per_conversation,
       DATE_TRUNC('day', archived_at)  as date
FROM session_archive_index
GROUP BY user_id, DATE_TRUNC('day', archived_at)
ORDER BY date DESC, user_id;

-- ==================== 创建触发器函数 ====================

-- ==================== 更新时间戳触发器函数 ====================
CREATE
OR
REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN NEW.updated_at = CURRENT_TIMESTAMP;
RETURN NEW;
END;
$$ language 'plpgsql';

-- 为需要自动更新updated_at字段的表创建触发器
CREATE TRIGGER update_document_updated_at
    BEFORE UPDATE
    ON document
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_tool_execution_audit_updated_at
    BEFORE UPDATE
    ON tool_execution_audit
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ==================== 数据清理函数 ====================

-- ==================== 清理过期审计数据函数 ====================
CREATE
OR
REPLACE FUNCTION cleanup_expired_audit_data(retention_days INTEGER DEFAULT 90)
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
-- 删除超过保留期的审计数据
DELETE
FROM tool_execution_audit
WHERE created_at < CURRENT_TIMESTAMP - INTERVAL '1 day' * retention_days;

GET DIAGNOSTICS deleted_count = ROW_COUNT;

-- 记录清理日志
RAISE NOTICE 'Cleaned up % expired audit records older than % days', deleted_count, retention_days;

RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

-- ==================== 清理过期会话数据函数 ====================
CREATE OR
REPLACE FUNCTION cleanup_expired_session_data(retention_days INTEGER DEFAULT 180)
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
-- 删除超过保留期的会话归档数据
DELETE
FROM session_archives
WHERE created_at < CURRENT_TIMESTAMP - INTERVAL '1 day' * retention_days;

GET DIAGNOSTICS deleted_count = ROW_COUNT;

-- 清理对应的索引记录
DELETE
FROM session_archive_index
WHERE archived_at < CURRENT_TIMESTAMP - INTERVAL '1 day' * retention_days;

-- 记录清理日志
RAISE NOTICE 'Cleaned up % expired session records older than % days', deleted_count, retention_days;

RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

-- ==================== 创建分区表（可选，用于大数据量场景） ====================

-- 注意：以下分区表创建语句仅在预期数据量很大时使用
-- 如果数据量不大，可以注释掉这部分

/*
-- 为审计表创建按月分区
CREATE TABLE tool_execution_audit_partitioned (
    LIKE tool_execution_audit INCLUDING ALL
) PARTITION BY RANGE (created_at);

-- 创建当前月份的分区
CREATE TABLE tool_execution_audit_y2024m12 PARTITION OF tool_execution_audit_partitioned
FOR VALUES FROM ('2024-12-01') TO ('2025-01-01');

-- 创建下个月的分区
CREATE TABLE tool_execution_audit_y2025m01 PARTITION OF tool_execution_audit_partitioned
FOR VALUES FROM ('2025-01-01') TO ('2025-02-01');
*/

-- ==================== 初始化数据 ====================

-- ==================== 插入系统配置数据 ====================
-- 可以在这里插入一些初始化的配置数据

-- 示例：插入系统默认配置（如果需要的话）
/*
INSERT INTO system_config (key, value, description) VALUES 
('max_chunk_size', '1000', '文档分块的最大字符数'),
('embedding_model', 'text-embedding-ada-002', '默认的嵌入模型'),
('vector_dimension', '1536', '向量维度')
ON CONFLICT (key) DO NOTHING;
*/

-- ==================== 数据库初始化完成 ====================

-- 输出初始化完成信息
DO $$
BEGIN RAISE NOTICE '===========================================';
RAISE NOTICE 'Spring AI Demo 2 数据库初始化完成！';
RAISE NOTICE '===========================================';
RAISE NOTICE '已创建的表：';
RAISE NOTICE '1. vector_store - Spring AI向量存储表';
RAISE NOTICE '2. document - 文档管理表';
RAISE NOTICE '3. document_chunk - 文档分块表';
RAISE NOTICE '4. document_file - 文档文件记录表';
RAISE NOTICE '5. session_archives - 会话归档主表';
RAISE NOTICE '6. session_archive_index - 会话归档索引表';
RAISE NOTICE '7. tool_execution_audit - 工具执行审计表';
RAISE NOTICE '8. parameter_chain - 参数转换链表';
RAISE NOTICE '9. decision_context - 决策上下文表';
RAISE NOTICE '10. performance_metrics - 性能指标表';
RAISE NOTICE '===========================================';
RAISE NOTICE '已创建的视图：';
RAISE NOTICE '1. audit_statistics - 审计统计视图';
RAISE NOTICE '2. session_statistics - 会话统计视图';
RAISE NOTICE '===========================================';
RAISE NOTICE '已创建的函数：';
RAISE NOTICE '1. cleanup_expired_audit_data() - 清理过期审计数据';
RAISE NOTICE '2. cleanup_expired_session_data() - 清理过期会话数据';
RAISE NOTICE '===========================================';
RAISE NOTICE '数据库已准备就绪，可以启动应用程序！';
RAISE NOTICE '===========================================';
END $$;

-- ==================== 使用说明 ====================
/*
使用说明：

1. 执行此脚本前，请确保：
   - PostgreSQL 16+ 已安装
   - PGVector 扩展已安装
   - 具有创建数据库和表的权限

2. 执行方式：
   psql -U postgres -d aidemo -f db/init.sql

3. 验证安装：
   - 检查表是否创建成功：\dt
   - 检查扩展是否启用：\dx
   - 检查索引是否创建：\di

4. 性能优化建议：
   - 根据实际数据量调整索引策略
   - 考虑使用分区表处理大数据量
   - 定期执行清理函数清理过期数据
   - 监控查询性能并优化慢查询

5. 维护建议：
   - 定期备份数据库
   - 监控磁盘空间使用情况
   - 定期更新统计信息：ANALYZE
   - 定期重建索引：REINDEX

6. 扩展说明：
   - 如需添加新表，请遵循现有的命名规范
   - 新增JSONB字段时记得创建GIN索引
   - 时间字段统一使用TIMESTAMP WITH TIME ZONE
   - 主键优先使用UUID，性能要求高的表可使用BIGSERIAL

7. 故障排查：
   - 如果向量索引创建失败，检查PGVector扩展是否正确安装
   - 如果权限错误，确保用户具有CREATE权限
   - 如果内存不足，考虑调整PostgreSQL配置参数
*/