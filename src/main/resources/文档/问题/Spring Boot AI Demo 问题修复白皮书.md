# Spring Boot AI Demo 问题修复白皮书

## 项目概述

本文档记录了 Spring Boot AI Demo 项目中遇到的启动异常和异步上传问题的完整诊断和修复过程。该项目是一个基于 Spring Boot 3.x
的 AI 知识库系统，集成了 Redis Stream、PostgreSQL 向量数据库和文档处理功能。

## 问题背景

### 系统架构

- **框架**: Spring Boot 3.3.5 + Spring AI 1.0.0
- **数据库**: PostgreSQL (带 pgvector 扩展)
- **缓存**: Redis (用于 Stream 消息队列)
- **文档处理**: Apache Tika + 智能文本切片
- **向量化**: 阿里云通义千问 text-embedding-v1

### 核心功能

- 文档上传与异步处理
- 智能文本切片与向量化
- Redis Stream 消息队列
- 知识库检索与问答

## 问题分析

### 问题 1: 启动异常

#### 错误现象

```
NoSuchBeanDefinitionException: No qualifying bean of type 
'StreamMessageListenerContainer<String, MapRecord<String, String, String>>' 
available: expected at least 1 bean which qualifies as autowire candidate
```

#### 根本原因

`SessionEventConsumer` 尝试通过 `@Qualifier("sessionEventContainer")` 注入 Bean，但 `RedisStreamConfig` 中只提供了工厂方法，没有使用
`@Bean` 注解注册到 Spring 容器。

#### 问题代码

```java
// RedisStreamConfig.java - 问题代码
public StreamMessageListenerContainer<String, MapRecord<String, String, String>> 
    createSessionEventContainer(RedisConnectionFactory connectionFactory) {
    // 工厂方法，但没有 @Bean 注解
}

// SessionEventConsumer.java - 问题代码
@Autowired
public SessionEventConsumer(
    @Qualifier("sessionEventContainer") StreamMessageListenerContainer<...> container) {
    // 尝试注入不存在的 Bean
}
```

### 问题 2: 异步上传异常

#### 错误现象

```
PSQLException: ERROR: insert or update on table "document_chunk" 
violates foreign key constraint "document_chunk_document_id_fkey"
Key (document_id)=(a338cd4a-43af-487d-b6e5-cdae0ceaed92) is not present in table "document".
```

#### 根本原因

`KnowledgeBaseService` 在插入 `document_chunk` 记录时，直接使用生成的 `documentId`，但没有先在 `document`
表中创建对应的父记录，违反了外键约束。

#### 数据库表结构

```sql
-- document 表 (父表)
CREATE TABLE document (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    title VARCHAR(255) NOT NULL,
    -- 其他字段...
);

-- document_chunk 表 (子表)
CREATE TABLE document_chunk (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    document_id UUID REFERENCES document(id), -- 外键约束
    content TEXT NOT NULL,
    -- 其他字段...
);
```

#### 问题代码

```java
// KnowledgeBaseService.java - 问题代码
public String ingest(String title, String content, Map<String, Object> metadata) {
    String documentId = UUID.randomUUID().toString();

    // 直接插入 document_chunk，但 document 表中没有对应记录
    DocumentChunk chunk = new DocumentChunk();
    chunk.setDocumentId(documentId); // 违反外键约束
    documentChunkMapper.insert(chunk);
}
```

## 解决方案

### 解决方案 1: 修复 Bean 注入问题

#### 修复策略

将 `RedisStreamConfig` 中的工厂方法改为 `@Bean` 方法，让 Spring 容器管理 `StreamMessageListenerContainer` 的生命周期。

#### 修复代码

```java
@Configuration
public class RedisStreamConfig {

    /**
     * Session Event Stream 监听容器 Bean
     * 由 SessionEventConsumer 使用
     */
    @Bean("sessionEventContainer")
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> 
        sessionEventContainer(RedisConnectionFactory connectionFactory) {
        
        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<...> options =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
                        .pollTimeout(Duration.ofMillis(100))
                        .serializer(new StringRedisSerializer())
                        .build();

        return StreamMessageListenerContainer.create(connectionFactory, options);
    }

    /**
     * Ingestion Stream 监听容器 Bean
     * 由 IngestionConsumer 使用
     */
    @Bean("ingestionContainer")
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> 
        ingestionContainer(RedisConnectionFactory connectionFactory) {
        
        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<...> options =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
                        .pollTimeout(Duration.ofMillis(100))
                        .serializer(new StringRedisSerializer())
                        .build();

        return StreamMessageListenerContainer.create(connectionFactory, options);
    }
}
```

#### 修复 IngestionConsumer

```java
@Component
public class IngestionConsumer implements StreamListener<String, MapRecord<String, String, String>> {
    
    @Autowired
    public IngestionConsumer(
        // 其他依赖...
        @Qualifier("ingestionContainer") StreamMessageListenerContainer<String, MapRecord<String, String, String>> container) {
        // 注入 Spring 管理的 Bean
        this.container = container;
    }
}
```

### 解决方案 2: 修复外键约束问题

#### 修复策略

在插入 `document_chunk` 之前，先在 `document` 表中创建对应的父记录，确保外键约束得到满足。

#### 修复代码

**1. 添加 DocumentMapper 依赖**

```java
@Service
public class KnowledgeBaseService {
    
    private final DocumentMapper documentMapper; // 新增
    
    public KnowledgeBaseService(
        // 其他依赖...
        DocumentMapper documentMapper) { // 新增
        this.documentMapper = documentMapper;
    }
}
```

**2. 修复 ingest 方法**

```java

@Transactional(rollbackFor = Exception.class)
public String ingest(String title, String content, Map<String, Object> metadata) {
    String documentId = UUID.randomUUID().toString();

    // 补充元数据
    if (metadata == null) metadata = new HashMap<>();
    metadata.put("title", title);
    metadata.put("source", "manual_ingest");

    // 0. 先插入 document 记录（满足外键约束）
    org.zerolg.aidemo2.entity.Document doc = new org.zerolg.aidemo2.entity.Document();
    doc.setId(documentId);
    doc.setTitle(title);
    doc.setMimeType((String) metadata.get("mime_type"));
    doc.setFilePath((String) metadata.get("file_path"));
    doc.setMetadata(metadata);
    doc.setCreatedAt(LocalDateTime.now());
    doc.setUpdatedAt(LocalDateTime.now());
    doc.setIsDeleted(false);

    // 1. 智能切片
    List<String> chunks = smartTextSplitter.split(content);
    logger.info("文本切片完成，生成 {} 个片段", chunks.size());

    // 设置切片数量和总 token 数
    doc.setChunkCount(chunks.size());
    doc.setTotalTokens(content.length());

    // 插入 document 记录
    documentMapper.insert(doc);
    logger.info("已创建文档记录: id={}, title={}", documentId, title);

    // 2. 后续处理 document_chunk...
    // 现在可以安全插入 document_chunk，因为父记录已存在
}
```

**3. 修复 ingestDocument 方法**

```java

@Transactional(rollbackFor = Exception.class)
public void ingestDocument(String ingestionId, String filePath, Map<String, Object> metadata) throws Exception {
    // 前面的处理逻辑...

    // 3.5 先插入 document 记录（满足外键约束）
    org.zerolg.aidemo2.entity.Document doc = new org.zerolg.aidemo2.entity.Document();
    doc.setId(ingestionId);
    doc.setTitle((String) metadata.getOrDefault("filename", "unknown"));
    doc.setFilePath(filePath);
    doc.setMimeType((String) metadata.get("mime_type"));
    doc.setMetadata(metadata);
    doc.setChunkCount(chunks.size());
    doc.setTotalTokens(text.length());
    doc.setCreatedAt(LocalDateTime.now());
    doc.setUpdatedAt(LocalDateTime.now());
    doc.setIsDeleted(false);
    documentMapper.insert(doc);
    logger.info("已创建文档记录: id={}, title={}", ingestionId, doc.getTitle());

    // 后续处理 document_chunk...
}
```

## 技术要点

### 1. Spring Bean 生命周期管理

- 使用 `@Bean` 注解让 Spring 容器管理对象生命周期
- 通过 `@Qualifier` 精确指定注入的 Bean
- 避免手动创建需要 Spring 管理的对象

### 2. 数据库事务与外键约束

- 使用 `@Transactional` 确保数据一致性
- 遵循外键约束，先插入父记录再插入子记录
- 合理设计表结构和实体关系

### 3. Redis Stream 消息队列

- 正确配置 `StreamMessageListenerContainer`
- 实现 `StreamListener` 接口处理消息
- 使用消费者组确保消息可靠处理

### 4. 类名冲突处理

- 使用完全限定类名避免 `org.zerolg.aidemo2.entity.Document` 与 `org.springframework.ai.document.Document` 冲突
- 合理组织包结构

## 验证结果

### 修复前

```
❌ 启动失败: NoSuchBeanDefinitionException
❌ 异步上传失败: PSQLException 外键约束违反
```

### 修复后

```
✅ 应用正常启动
✅ Redis Stream 容器正常初始化
✅ 异步文档上传处理成功
✅ 数据库外键约束满足
✅ 向量化入库正常
```

## 最佳实践建议

### 1. 依赖注入

- 优先使用 Spring 容器管理对象生命周期
- 避免在业务代码中手动创建 Spring 管理的对象
- 使用 `@Qualifier` 明确指定注入的 Bean

### 2. 数据库设计

- 设计外键约束时考虑插入顺序
- 使用事务确保数据一致性
- 合理设计实体关系映射

### 3. 异步处理

- 使用 Redis Stream 实现可靠的异步消息处理
- 实现消息确认机制避免消息丢失
- 合理设计消费者组和错误处理

### 4. 错误处理

- 实现完善的异常处理和状态更新
- 记录详细的日志便于问题排查
- 设计合理的重试和降级机制

## 总结

本次修复解决了两个关键问题：

1. **Spring Bean 管理问题**: 通过正确使用 `@Bean` 注解，让 Spring 容器管理 `StreamMessageListenerContainer` 的生命周期
2. **数据库外键约束问题**: 通过在插入子记录前先创建父记录，确保外键约束得到满足

修复后的系统具备了完整的文档异步处理能力，包括文件上传、文档解析、智能切片、向量化和入库等功能。系统架构更加健壮，符合 Spring
Boot 最佳实践。

---

**文档版本**: v1.0  
**修复日期**: 2024年12月29日  
**技术栈**: Spring Boot 3.3.5, Spring AI 1.0.0, PostgreSQL, Redis, Apache Tika