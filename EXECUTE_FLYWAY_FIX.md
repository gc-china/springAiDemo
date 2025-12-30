# 执行Flyway修复步骤

## 方案2：手动创建Flyway历史表

### 步骤1：连接到数据库

```bash
psql -U postgres -d aidemo
```

### 步骤2：执行修复脚本

在psql命令行中执行：

```sql
\i create_flyway_history.sql
```

或者直接执行：

```bash
psql -U postgres -d aidemo -f create_flyway_history.sql
```

### 步骤3：验证结果

执行后应该看到：

```
 installed_rank | version |    description     | type | script                    | checksum   | installed_by | installed_on        | execution_time | success 
----------------+---------+--------------------+------+---------------------------+------------+--------------+---------------------+----------------+---------
              1 | 1       | Create audit tables| SQL  | V1__Create_audit_tables.sql| -619757082 | manual       | 2025-12-29 19:xx:xx |              0 | t
```

### 步骤4：启动应用

```bash
mvn spring-boot:run
```

## 如果仍有问题

### 检查审计表是否存在

```sql
SELECT table_name FROM information_schema.tables 
WHERE table_schema = 'public' 
AND table_name IN ('tool_execution_audit', 'parameter_chain', 'decision_context', 'performance_metrics');
```

### 如果表不存在，手动创建

```bash
psql -U postgres -d aidemo -f manual_audit_tables_setup.sql
```

### 重新执行Flyway修复

```bash
psql -U postgres -d aidemo -f create_flyway_history.sql
```