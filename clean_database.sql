-- 完全重置数据库脚本
-- 警告：这将删除所有数据！

-- 1. 连接到PostgreSQL默认数据库
-- psql -U postgres -d postgres

-- 2. 强制断开所有连接并删除数据库
SELECT pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE datname = 'aidemo';
DROP DATABASE IF EXISTS aidemo;
CREATE DATABASE aidemo;

-- 3. 连接到新数据库
-- \c aidemo

-- 4. 验证数据库为空
SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public';

-- 现在可以启动应用，Flyway将自动创建所有表