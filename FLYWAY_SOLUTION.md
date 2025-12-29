# Flyway问题完整解决方案

## 问题分析

Flyway检测到数据库中有表但没有schema history表，这通常发生在：

1. 数据库之前手动创建了表
2. Flyway配置不正确
3. 数据库迁移历史丢失

## 解决方案（选择其一）

### 方案1：清理数据库重新开始（推荐，最干净）

1. **备份重要数据**（如果有）
2. **运行清理脚本**：
   ```bash
   psql -U postgres -d postgres -f clean_database.sql
   ```
3. **启动应用**：
   ```bash
   mvn spring-boot:run
   ```
   Flyway将自动创建所有表

### 方案2：手动创建Flyway历史表（保留现有数据）

1. **创建Flyway历史表**：
   ```bash
   psql -U postgres -d aidemo -f create_flyway_history.sql
   ```
2. **启动应用**：
   ```bash
   mvn spring-boot:run
   ```

### 方案3：使用Flyway命令行工具

1. **安装Flyway CLI**（如果没有）
2. **执行baseline**：
   ```bash
   flyway -url=jdbc:postgresql://localhost:5432/aidemo -user=postgres -password=postgres baseline
   ```
3. **启动应用**

## 验证步骤

启动成功后，检查以下内容：

1. **数据库表**：
   ```sql
   SELECT table_name FROM information_schema.tables WHERE table_schema = 'public';
   ```
   应该看到：
    - flyway_schema_history
    - tool_execution_audit
    - parameter_chain
    - decision_context
    - performance_metrics

2. **审计功能**：
    - 访问 http://localhost:8888
    - 点击"审计监控"标签
    - 应该能看到审计面板

3. **API测试**：
   ```bash
   curl http://localhost:8888/api/audit/summary
   ```

## 如果仍有问题

1. **检查PostgreSQL连接**：
   ```bash
   psql -U postgres -d aidemo -c "SELECT version();"
   ```

2. **查看详细日志**：
   在application.yml中添加：
   ```yaml
   logging:
     level:
       org.flywaydb: DEBUG
       org.zerolg.aidemo2.audit: DEBUG
   ```

3. **手动运行迁移**：
   ```sql
   -- 连接到数据库并手动运行V1__Create_audit_tables.sql的内容
   ```

## 预防措施

1. **定期备份数据库**
2. **不要手动修改Flyway管理的表**
3. **使用版本控制管理迁移脚本**
4. **在生产环境中谨慎使用baseline**