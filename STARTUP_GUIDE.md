# 应用启动指南

## 当前状态

- ✅ 审计系统已完全禁用 (`audit.enabled: false`)
- ✅ Flyway数据库迁移已禁用 (`flyway.enabled: false`)
- ✅ 所有审计相关组件都有条件注解，只有在启用时才会加载

## 启动步骤

### 1. 直接启动（推荐）

应用现在应该可以正常启动，因为所有可能导致问题的组件都已禁用。

```bash
mvn spring-boot:run
```

### 2. 如果需要启用审计系统

#### 选项A：使用内存存储（简单）

```yaml
audit:
  enabled: true
  storage:
    type: memory
```

#### 选项B：使用数据库存储（需要先创建表）

1. 首先手动创建审计表：
   ```sql
   -- 运行 manual_audit_tables_setup.sql 中的SQL
   ```

2. 然后启用审计：
   ```yaml
   audit:
     enabled: true
     storage:
       type: database
   ```

#### 选项C：使用Flyway自动创建表

1. 清空数据库：
   ```sql
   DROP DATABASE IF EXISTS aidemo;
   CREATE DATABASE aidemo;
   ```

2. 启用Flyway和审计：
   ```yaml
   spring:
     flyway:
       enabled: true
   audit:
     enabled: true
     storage:
       type: database
   ```

## 功能状态

### ✅ 可用功能

- AI对话系统
- 知识库管理
- 参数修正系统
- 库存工具（IntegratedInventoryTools）
- 系统监控

### ❌ 暂时禁用的功能

- 审计和链路追踪
- 审计监控面板
- 数据库审计存储

## 故障排除

如果仍然遇到启动问题：

1. 检查PostgreSQL连接
2. 确认Redis连接（如果使用）
3. 查看完整的错误日志
4. 考虑临时禁用更多组件

## 重新启用审计系统

当你准备好启用审计系统时：

1. 选择上述选项之一设置数据库
2. 修改 `application.yml` 中的 `audit.enabled: true`
3. 重启应用
4. 访问 http://localhost:8888 查看审计监控面板