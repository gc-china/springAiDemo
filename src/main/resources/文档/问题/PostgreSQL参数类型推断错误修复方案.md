# PostgreSQL 参数类型推断错误修复方案

## 问题描述

在执行全文检索时出现 PostgreSQL 错误：

```
ERROR: could not determine data type of parameter $1
```

**错误 SQL**：

```sql
SELECT * FROM document_chunk WHERE content ILIKE CONCAT('%', ?, '%') LIMIT ?
```

## 根本原因

PostgreSQL 在使用 `CONCAT` 函数时，无法自动推断 MyBatis 传入的参数类型，特别是当参数位置在函数内部时。

## 解决方案

### 方案1：使用 PostgreSQL 字符串连接操作符（推荐）

```java
@Select("SELECT * FROM document_chunk WHERE content ILIKE ('%' || #{query} || '%') LIMIT #{limit}")
List<DocumentChunk> searchByKeyword(@Param("query") String query, @Param("limit") int limit);
```

**优点**：

- PostgreSQL 原生操作符，性能好
- 类型推断清晰
- 语法简洁

### 方案2：显式类型转换

```java
@Select("SELECT * FROM document_chunk WHERE content ILIKE ('%' || #{query}::text || '%') LIMIT #{limit}")
List<DocumentChunk> searchByKeyword(@Param("query") String query, @Param("limit") int limit);
```

**优点**：

- 明确指定参数类型
- 避免类型推断问题

### 方案3：MyBatis 动态 SQL 与 JDBC 类型

```java
@Select("<script>" +
        "SELECT * FROM document_chunk " +
        "WHERE content ILIKE CONCAT('%', #{query,jdbcType=VARCHAR}, '%') " +
        "LIMIT #{limit,jdbcType=INTEGER}" +
        "</script>")
List<DocumentChunk> searchByKeyword(@Param("query") String query, @Param("limit") int limit);
```

**优点**：

- 明确指定 JDBC 类型
- 兼容性好

## PostgreSQL 字符串操作符对比

| 操作符        | 功能     | 示例                                  | 性能 |
|------------|--------|-------------------------------------|----|
| `\|\|`     | 字符串连接  | `'Hello' \|\| ' World'`             | 高  |
| `CONCAT()` | 函数连接   | `CONCAT('Hello', ' World')`         | 中  |
| `FORMAT()` | 格式化字符串 | `FORMAT('%s %s', 'Hello', 'World')` | 低  |

## 类型推断问题的常见场景

### 1. 函数参数位置

```sql
-- 问题：参数在函数内部
CONCAT('%', ?, '%')

-- 解决：使用操作符
'%' || ? || '%'
```

### 2. 复杂表达式

```sql
-- 问题：嵌套函数调用
UPPER(CONCAT(?, '%'))

-- 解决：分步处理或显式转换
UPPER(? || '%')
UPPER(?::text || '%')
```

### 3. 条件表达式

```sql
-- 问题：CASE 表达式中的参数
CASE WHEN ? IS NULL THEN 'default' ELSE ? END

-- 解决：显式类型转换
CASE WHEN ?::text IS NULL THEN 'default' ELSE ?::text END
```

## MyBatis 参数处理最佳实践

### 1. 使用 @Param 注解

```java
List<DocumentChunk> searchByKeyword(@Param("query") String query, @Param("limit") int limit);
```

### 2. 指定 JDBC 类型（可选）

```java
#{query,jdbcType=VARCHAR}
#{limit,jdbcType=INTEGER}
```

### 3. 使用动态 SQL 处理复杂逻辑

```xml
<select id="searchByKeyword" resultType="DocumentChunk">
    SELECT * FROM document_chunk 
    WHERE content ILIKE ('%' || #{query} || '%')
    LIMIT #{limit}
</select>
```

## 修改文件

- `src/main/java/org/zerolg/aidemo2/mapper/DocumentChunkMapper.java`
    - 修改 `searchByKeyword` 方法的 SQL 语句
    - 使用 PostgreSQL 字符串连接操作符 `||`

## 验证步骤

1. **重启应用程序**
2. **测试全文检索**：
    - 在问答界面输入包含关键词的问题
    - 检查是否能正常检索到相关文档
    - 确认不再出现参数类型错误

3. **检查日志**：
    - 观察 SQL 执行日志
    - 确认 SQL 语句正确执行

## 预期效果

修复后：

- 全文检索功能正常工作
- 不再出现 PostgreSQL 参数类型错误
- RAG 混合检索（向量 + 关键词）完整可用
- 问答质量提升（支持精确关键词匹配）

## 相关知识

### PostgreSQL 字符串函数性能对比

1. **`||` 操作符**：最快，直接的字符串连接
2. **`CONCAT()` 函数**：中等，函数调用开销
3. **`FORMAT()` 函数**：最慢，复杂的格式化处理

### MyBatis 类型映射

- Java `String` → PostgreSQL `text/varchar`
- Java `int` → PostgreSQL `integer`
- 自动类型推断在简单场景下工作良好
- 复杂表达式建议显式指定类型

通过使用 PostgreSQL 原生的字符串连接操作符，我们避免了类型推断问题，同时获得了更好的性能。