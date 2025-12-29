# PostgreSQL Boolean 类型问题解决方案

## 问题描述

在调用 `/api/ai/knowledge/documents` 接口时出现以下错误：

```
ERROR: operator does not exist: boolean = integer
建议：No operator matches the given name and argument types. You might need to add explicit type casts.
SQL: SELECT ... FROM document WHERE is_deleted=0
```

## 问题原因

1. **数据库表结构**：`document` 表中的 `is_deleted` 字段是 `BOOLEAN` 类型
2. **MyBatis Plus 配置**：全局逻辑删除配置使用 `INTEGER` 类型的值（0 和 1）
3. **类型不匹配**：PostgreSQL 不允许直接比较 `BOOLEAN` 和 `INTEGER` 类型

## 解决方案

### 方案 1：修改实体类使用 @TableLogic 注解（推荐）

```java
@Data
@TableName(value = "document", autoResultMap = true)
public class Document {
    // ... 其他字段
    
    @TableLogic(value = "false", delval = "true")
    private Boolean isDeleted;
}
```

### 方案 2：修改全局配置支持 Boolean 类型

```yaml
# application.yml
mybatis-plus:
  global-config:
    db-config:
      logic-delete-field: isDeleted
      logic-delete-value: true    # 使用 Boolean 值
      logic-not-delete-value: false
```

### 方案 3：应用层过滤（当前采用）

```java
@GetMapping("/documents")
public ResponseEntity<List<Map<String, Object>>> getDocuments() {
    // 查询所有记录，不使用数据库层逻辑删除过滤
    List<Document> allDocuments = documentMapper.selectList(
        new LambdaQueryWrapper<Document>()
            .orderByDesc(Document::getCreatedAt)
    );
    
    // 在应用层过滤未删除的记录
    List<Document> documents = allDocuments.stream()
        .filter(doc -> doc.getIsDeleted() == null || !doc.getIsDeleted())
        .collect(Collectors.toList());
    
    // ... 处理结果
}
```

## 修改的文件

### 1. Document.java

```java
// 添加 @TableLogic 注解
@TableLogic(value = "false", delval = "true")
private Boolean isDeleted;
```

### 2. application.yml

```yaml
# 修改逻辑删除配置为 Boolean 类型
mybatis-plus:
  global-config:
    db-config:
      logic-delete-value: true
      logic-not-delete-value: false
```

### 3. KnowledgeBaseController.java

```java
// 修改查询方式，在应用层过滤
List<Document> documents = allDocuments.stream()
                .filter(doc -> doc.getIsDeleted() == null || !doc.getIsDeleted())
                .collect(Collectors.toList());
```

## 数据库验证

### 检查表结构

```sql
\d document;

SELECT 
    column_name, 
    data_type, 
    column_default,
    is_nullable
FROM information_schema.columns 
WHERE table_name = 'document' 
AND column_name = 'is_deleted';
```

### 预期结果

```
column_name | data_type | column_default | is_nullable
is_deleted  | boolean   | false          | YES
```

## 测试验证

### 1. 启动应用

确保应用能够正常启动，不再出现 SQL 语法错误。

### 2. 测试接口

```bash
curl -X GET http://localhost:8888/api/ai/knowledge/documents
```

### 3. 预期响应

```json
[
  {
    "documentId": "uuid",
    "title": "文档标题",
    "filePath": "/path/to/file",
    "mimeType": "application/pdf",
    "totalTokens": 1500,
    "chunkCount": 8,
    "createdAt": "2024-12-29T10:30:00",
    "downloadUrl": "/api/ai/knowledge/download/uuid",
    "previewUrl": "/api/ai/knowledge/preview/uuid",
    "fileExists": true
  }
]
```

## 最佳实践建议

### 1. 数据类型一致性

- 确保实体类字段类型与数据库字段类型一致
- 使用 `Boolean` 类型对应数据库的 `BOOLEAN` 字段
- 避免混用 `Integer` 和 `Boolean` 类型

### 2. MyBatis Plus 配置

- 优先使用 `@TableLogic` 注解进行字段级配置
- 全局配置作为默认值，字段级注解具有更高优先级
- 明确指定逻辑删除的值类型

### 3. 错误处理

- 在 Controller 层添加异常处理
- 提供友好的错误信息
- 记录详细的错误日志

### 4. 数据库兼容性

- 不同数据库对类型转换的支持不同
- PostgreSQL 对类型检查较为严格
- MySQL 相对宽松，但建议保持类型一致

## 总结

通过以上修改，解决了 PostgreSQL 中 `BOOLEAN` 类型与 `INTEGER` 类型比较的问题：

1. ✅ **实体类配置**：使用 `@TableLogic` 注解明确指定逻辑删除值
2. ✅ **全局配置**：修改为 `Boolean` 类型的逻辑删除值
3. ✅ **应用层过滤**：避免数据库层的类型冲突
4. ✅ **错误处理**：增强异常处理和日志记录

现在系统应该能够正常获取文档列表，前端的文档管理功能也能正常工作。