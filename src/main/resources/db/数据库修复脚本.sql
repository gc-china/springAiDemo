-- 数据库修复脚本：解决 Boolean 类型不匹配问题
-- 执行此脚本前请备份数据库

-- 1. 检查当前 document 表结构
\
d document;

-- 2. 如果 is_deleted 字段不是 BOOLEAN 类型，需要修改
-- 首先检查是否有数据
SELECT COUNT(*)
FROM document;

-- 3. 如果表中有数据，先备份
-- CREATE TABLE document_backup AS SELECT * FROM document;

-- 4. 修改字段类型（如果需要）
-- 如果字段是 INTEGER 类型，需要转换为 BOOLEAN
-- ALTER TABLE document ALTER COLUMN is_deleted TYPE BOOLEAN USING (is_deleted::INTEGER != 0);

-- 5. 确保默认值正确
ALTER TABLE document
    ALTER COLUMN is_deleted SET DEFAULT FALSE;

-- 6. 检查修复结果
SELECT column_name,
       data_type,
       column_default,
       is_nullable
FROM information_schema.columns
WHERE table_name = 'document'
  AND column_name = 'is_deleted';

-- 7. 如果表为空，可以直接重建表
-- DROP TABLE IF EXISTS document CASCADE;
-- 然后重新运行 init.sql 脚本

-- 8. 验证数据
SELECT id, title, is_deleted
FROM document
LIMIT 5;