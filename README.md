# 项目完整认知框架

## 项目定位与目标

### 一句话概述
构建一个以大模型为核心、数据驱动、工具可编排（orchestratable）、并具备可观测/可审计/可降级能力的认知型 Agent 平台，面向企业级多租户生产场景。

### 核心价值主张
1. **智能工具发现**：自动扫描并注册所有 `@Bean` 定义的 `Function` 工具
2. **混合调用策略**：
   - **读操作（查询）**：参数矫正策略，自动修复模糊输入
   - **写操作（调拨）**：人机确认策略，防止 AI 误操作
3. **RAG（检索增强生成）**：结合向量数据库提供知识库问答能力

---

## 技术栈全景

### 后端框架
- **Spring Boot**: 3.3.5
- **Spring AI**: 1.0.0
- **Spring AI Alibaba**: 1.1.0.0-M5 (DashScope)
- **Spring AI Ollama**: 1.1.0
- **Spring AOP**: 用于参数矫正切面

### LLM 服务
- **DashScope (通义千问)**: 主要推理和 embedding 服务
- **Ollama**: 本地模型支持

### 数据存储
- **当前**: SimpleVectorStore (JSON 文件)
- **目标**: PostgreSQL + PGVector
- **会话**: Redis (List/Stream/Hash + 归档到 PG/S3)

### 消息队列（计划）
- Kafka / RabbitMQ / Pulsar

### 可观测性（计划）
- **监控**: Prometheus + Grafana
- **追踪**: Jaeger
- **日志**: Loki

### 基础设施（计划）
- **容器编排**: Kubernetes + Helm
- **IaC**: Terraform

---

## 系统架构详解

### 整体组件
```
┌─────────────────────────────────────────────────────────────┐
│                      API Gateway                            │
│              (鉴权、流量控制、租户隔离)                        │
└──────────────────────┬──────────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────────┐
│              Agent Orchestrator                             │
│         (Spring Boot + Spring AI)                           │
│  ┌──────────────┬──────────────┬──────────────────────┐    │
│  │ 意图识别      │ 流程编排      │ Tool 路由             │    │
│  └──────────────┴──────────────┴──────────────────────┘    │
└──────┬────────────────┬────────────────┬───────────────────┘
       │                │                │
┌──────▼─────┐  ┌───────▼──────┐  ┌─────▼──────────┐
│ 会话记忆    │  │ 向量知识库    │  │ Tooling 层      │
│ (Redis)    │  │ (PGVector)   │  │ (ERP/Inventory)│
└────────────┘  └──────────────┘  └────────────────┘
       │                │
┌──────▼────────────────▼──────┐
│      模型服务 (DashScope)     │
│   (推理 + Embedding)         │
└──────────────────────────────┘
```

### 分层架构
```
用户层 (User)
    ↓
控制器层 (AiController)
    ↓
服务层 (AiService)
    ├─→ RagService (检索与重排序)
    ├─→ ChatClient (LLM 交互)
    └─→ ToolRegistry (工具注册中心)
    ↓
工具执行层
    ├─→ ArgumentCorrectionAspect (参数矫正切面)
    ├─→ InventoryTools (库存工具集)
    │   ├─→ queryStock (查询 - 方案四)
    │   └─→ transferStock (调拨 - 方案三)
    └─→ MockSearchService (模糊搜索引擎)
    ↓
业务服务层 (InventoryService)
```

---

## 核心设计模式与策略

### 四种 Function Calling 策略

#### 方案一：前置意图识别 (NLU) - "闪电快手"
- **原理**: 路由器模式，使用正则或轻量级 NLP 模型毫秒级判断意图
- **适用**: 高频简单指令（如开关灯）
- **价值**: 极速响应 + 零成本，拦截 80% 简单指令

#### 方案二：前端表单辅助 (Client Forms) - "填表专家"
- **原理**: UI 降级策略，LLM 只负责唤起 UI 界面
- **适用**: 复杂参数收集（如入库单）
- **价值**: 100% 准确率 + 高效录入

#### 方案三：人机协同确认 (Human-in-the-loop) - "安全卫士" ⭐
- **原理**: 两阶段提交（Prepare + Commit）
- **实现**: `TransferRequest` 的 `confirmed` 字段
- **流程**:
  1. 阶段一：`confirmed=false`，返回确认文案，不执行业务
  2. 阶段二：用户确认后 `confirmed=true`，执行真实操作
- **适用**: 写操作（调拨、退款等）
- **价值**: 兜底安全，防止 AI 幻觉导致业务灾难

#### 方案四：模糊搜索与参数矫正 (Fuzzy Search) - "智能翻译官" ⭐
- **原理**: AOP 拦截 + 搜索引擎矫正
- **实现**: `ArgumentCorrectionAspect` + `MockSearchService`
- **流程**:
  1. LLM 提取参数（如 "苹果15"）
  2. AOP 拦截，调用搜索引擎模糊匹配
  3. 决策：
     - **唯一匹配**: 替换为精确 ID，放行
     - **多个匹配**: 返回歧义提示
     - **无匹配**: 返回错误
- **适用**: 读操作（查询库存）
- **价值**: 鲁棒性，弥合"人类模糊语言"与"机器精确数据"鸿沟

---

## 核心组件详解

### 1. AiService (智能服务入口)
**职责**:
- 与 LLM 交互
- 管理对话上下文
- 处理 RAG 检索
- 挂载可用工具

**关键流程**:
```java
public Flux<String> processQuery(String chatId, String msg) {
    // 1. RAG 检索与重排序
    return ragService.retrieveAndRerank(msg)
        .flatMapMany(finalDocuments -> {
            // 2. 构建 Prompt（注入上下文）
            String context = ...;
            String systemText = ...;
            
            // 3. 调用 AI（流式）
            return chatClient.prompt()
                .system(systemText)
                .user(msg)
                .advisors(a -> a
                    .param("chat_memory_conversation_id", chatId)
                    .param("chat_memory_response_size", 10)
                )
                .toolNames(availableTools)
                .stream()
                .content();
        });
}
```

### 2. ToolRegistry (工具注册中心)
**职责**:
- 自动发现 Spring 容器中所有 `Function` Bean
- 提供工具分类管理
- 支持按需获取工具列表

**核心方法**:
```java
@Bean
public List<String> availableToolNames() {
    Map<String, Function> functionBeans = 
        applicationContext.getBeansOfType(Function.class);
    return new ArrayList<>(functionBeans.keySet());
}
```

### 3. InventoryTools (工具定义)
**包含工具**:
- `queryStock`: 查询库存（配合 AOP 使用 - 方案四）
- `transferStock`: 调拨库存（内置两阶段确认 - 方案三）

**queryStock 实现**:
```java
@Bean
@Description("查询库存数量。支持模糊名称查询，系统会自动矫正")
public Function<StockQueryRequest, String> queryStock() {
    return request -> {
        String rawName = request.product();
        
        // 如果是 ID，直接查询
        if (rawName.startsWith("P-")) {
            return "库存为: " + inventoryService.getStock(rawName);
        }
        
        // 模糊搜索
        List<SearchResult> matches = searchService.fuzzySearch(rawName);
        
        if (matches.size() == 1) {
            // 唯一匹配，自动矫正
            return "库存为: " + inventoryService.getStock(matches.get(0).id());
        } else if (matches.size() > 1) {
            // 歧义
            return "找到多个相关产品: ...";
        } else {
            // 无匹配
            return "未找到产品";
        }
    };
}
```

**transferStock 实现**:
```java
@Bean
@Description("调拨库存。这是一个敏感操作，需要用户确认")
public Function<TransferRequest, String> transferStock() {
    return request -> {
        if (!request.confirmed()) {
            // 阶段一：返回确认单
            return "⚠️ 操作确认\n您申请将 ... 请回复"确认"";
        } else {
            // 阶段二：执行操作
            inventoryService.transferStock(...);
            return "✅ 调拨执行成功！";
        }
    };
}
```

### 4. ArgumentCorrectionAspect (参数矫正切面)
**职责**: 拦截查询请求，解决"用户输入模糊"问题

**逻辑**:
```java
@Around("execution(* queryStock(..))")
public Object correctArgs(ProceedingJoinPoint joinPoint) {
    String rawName = (String) joinPoint.getArgs()[0];
    List<Product> matches = searchService.search(rawName);
    
    if (matches.size() == 1) {
        // 唯一匹配，替换参数
        return joinPoint.proceed(new Object[]{matches.get(0).getId()});
    } else if (matches.size() > 1) {
        // 歧义
        return "找到多个商品: ...";
    } else {
        return "没找到这个商品";
    }
}
```

### 5. RagService (RAG 服务)
**职责**:
- 向量检索
- 文档重排序
- 上下文构建

**流程**:
```java
public Mono<List<Document>> retrieveAndRerank(String query) {
    // 1. 向量检索
    List<Document> retrieved = vectorStore.similaritySearch(
        SearchRequest.query(query).withTopK(topK)
    );
    
    // 2. 过滤低相似度
    List<Document> filtered = retrieved.stream()
        .filter(doc -> doc.getMetadata().get("distance") < threshold)
        .collect(Collectors.toList());
    
    // 3. 重排序（TODO: 集成 Reranker）
    return Mono.just(filtered);
}
```

---

## 数据流与交互流程

### 智能查询流程（读操作）
```
用户: "查一下苹果15"
    ↓
LLM: 调用 queryStock("苹果15")
    ↓
AOP 切面: 拦截模糊参数
    ↓
搜索引擎: 模糊搜索 "苹果15"
    ↓
搜索引擎: 返回 ID "P-001"
    ↓
AOP 切面: 替换参数为 "P-001"
    ↓
InventoryService: 查询库存
    ↓
用户: 收到库存结果
```

### 安全调拨流程（写操作）
```
用户: "把货发走"
    ↓
LLM: 调用 transferStock(confirmed=false)
    ↓
工具: 未确认，拦截！
    ↓
用户: 收到 "请确认操作..."
    ↓
用户: "确认"
    ↓
LLM: 调用 transferStock(confirmed=true)
    ↓
工具: 已确认，执行！
    ↓
InventoryService: 执行调拨
    ↓
用户: "操作成功"
```

### RAG 增强查询流程
```
用户: "什么是 RAG？"
    ↓
AiService: processQuery(chatId, msg)
    ↓
RagService: retrieveAndRerank(msg)
    ├─→ VectorStore: similaritySearch (Top 8)
    ├─→ 过滤低相似度文档
    └─→ 返回最终文档列表
    ↓
AiService: 构建 Prompt
    ├─→ 注入上下文（文档内容）
    └─→ 注入历史记忆（最近 10 条）
    ↓
ChatClient: 调用 LLM（流式）
    ├─→ system(systemText)
    ├─→ user(msg)
    ├─→ advisors (chat_memory)
    └─→ toolNames(availableTools)
    ↓
用户: 收到流式响应
```

---

## 核心设计原则

### 1. 契约优先
- OpenAPI/Protobuf
- 接口版本化与向后兼容

### 2. 可观测与可追溯
- Trace-id 全链路贯穿
- 所有关键步骤打点并可回溯

### 3. 防御式设计
- 输入校验 → 熔断 → 重试 → 降级路径

### 4. 安全与合规
- 默认 TLS
- KMS 加密
- PII 掩码
- 最小权限

### 5. 成本可控
- 语义缓存
- 模型分层
- token budget
- 按租户限额

---

## 关键技术决策

### 为什么选择 Spring AI？
1. **原生 Spring 集成**: 无缝集成 Spring Boot 生态
2. **工具自动发现**: `Function` Bean 自动注册
3. **多模型支持**: DashScope、Ollama、OpenAI 等
4. **RAG 内置支持**: VectorStore、DocumentReader 等
5. **流式响应**: Reactive Streams (Flux)

### 为什么使用 AOP？
1. **低侵入性**: 业务逻辑无需修改
2. **关注点分离**: 参数矫正逻辑独立
3. **可复用**: 一次定义，多处使用
4. **可测试**: 切面逻辑可单独测试

### 为什么选择两阶段提交？
1. **安全第一**: 防止 AI 幻觉导致误操作
2. **用户可控**: 用户明确知道将要执行的操作
3. **可审计**: 每次确认都有记录
4. **易实现**: 只需一个 `confirmed` 字段

---

## 风险与缓解策略

### 风险 1: LLM 幻觉率高
**缓解**:
- 增加 reranker
- evidence-first prompt
- verifier 模块
- 严格引用策略

### 风险 2: 调用成本飙升
**缓解**:
- 语义缓存
- 模型分层（小模型缓存/大模型推理）
- token budget
- 流量限额

### 风险 3: 会话乱序或丢失
**缓解**:
- 使用 Redis Stream
- 持久化归档
- 写入 DLQ
- stream lag 监控

### 风险 4: 数据泄露（PII）
**缓解**:
- 入参/出参掩码
- KMS 管理
- 审计日志
- 权限控制与审计

### 风险 5: 检索性能瓶颈
**缓解**:
- 表分区
- read replicas
- index 调优
- reranker 缓存

---

## 关键配置

### application.yml
```yaml
spring:
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}

ai:
  rag:
    topK: 8
    similarityThreshold: 0.4
```

### pom.xml 关键依赖
```xml
<!-- Spring Boot -->
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.5</version>
</parent>

<!-- Spring AI -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-bom</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- DashScope -->
<dependency>
    <groupId>com.alibaba.cloud.ai</groupId>
    <artifactId>spring-ai-alibaba-starter-dashscope</artifactId>
    <version>1.1.0.0-M5</version>
</dependency>

<!-- AOP -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

---

## 项目文件结构

```
spring-aidemo/
├── src/main/java/org/zerolg/aidemo2/
│   ├── AiDemo2Application.java          # 主应用
│   ├── aspect/
│   │   ├── ArgumentCorrectionAspect.java # 参数矫正切面
│   │   └── WebLogAspect.java            # Web 日志切面
│   ├── common/
│   │   ├── ApiResponse.java             # 统一响应
│   │   ├── BusinessException.java       # 业务异常
│   │   └── ResultCode.java              # 结果码
│   ├── config/
│   │   ├── AiConfig.java                # AI 配置
│   │   ├── KnowledgeIngestionService.java # 知识入库
│   │   ├── ToolRegistry.java            # 工具注册中心
│   │   ├── VectorStoreConfig.java       # 向量存储配置
│   │   └── WebConfig.java               # Web 配置
│   ├── controller/
│   │   └── AiController.java            # AI 控制器
│   ├── exception/
│   │   └── GlobalExceptionHandler.java  # 全局异常处理
│   ├── service/
│   │   ├── AiService.java               # AI 服务
│   │   ├── InventoryService.java        # 库存服务
│   │   ├── MockSearchService.java       # 搜索服务接口
│   │   ├── RagService.java              # RAG 服务
│   │   └── impl/
│   │       └── MockSearchServiceImpl.java # 搜索服务实现
│   └── tools/
│       └── InventoryTools.java          # 库存工具
├── src/main/resources/
│   ├── static/
│   │   └── rag-enhanced-prompt.st       # RAG 提示词模板
│   └── 文档/
│       ├── Agent 解决方案.MD             # 企业级解决方案
│       ├── LLM调用工具混合策略3.md       # 混合策略详解
│       ├── 自动发现和注册工具类说明.md    # 工具注册说明
│       ├── task.md                      # 项目任务清单
│       └── ...
├── vectorstore.json                     # 向量存储（临时）
├── pom.xml                              # Maven 配置
└── README.md                            # 项目说明
```

---

## 下一步行动建议

### 短期（1-2 周）
1. **Redis 集成**: 实现会话存储层
2. **PostgreSQL + PGVector**: 迁移向量存储
3. **混合检索**: 实现向量 + 全文检索
4. **监控基础**: 添加 Prometheus metrics

### 中期（3-4 周）
1. **Reranker 集成**: 提升检索质量
2. **异步 ETL**: 实现文档入库 pipeline
3. **Slot Filling**: 实现状态机
4. **前端优化**: 思维链可视化

### 长期（5-12 周）
1. **多租户**: 实现租户隔离
2. **安全加固**: PII 掩码、KMS 等
3. **LLMOps**: 完整监控与告警
4. **CI/CD**: 自动化部署

---

## 学习资源

### 必读文档
- Agent 解决方案.MD（企业级完整方案）
- LLM调用工具混合策略3.md（四种策略详解）
- 自动发现和注册工具类说明.md（工具注册机制）
- task.md（项目任务清单）

### 推荐论文
- Attention Is All You Need
- Chain-of-Thought Prompting
- ReAct: Synergizing Reasoning and Acting

### 工具与库
- Spring AI 官方文档
- LangChain 文档
- Milvus / PGVector 官方指南

---

## 总结

这是一个**工程级**的企业 AI Agent 平台，不是简单的 Demo。核心特点：

1. **自动化**: 工具自动发现，无需手动注册
2. **智能化**: 参数自动矫正，模糊输入也能理解
3. **安全化**: 两阶段确认，防止误操作
4. **工程化**: AOP 切面，低侵入性
5. **可扩展**: 分层架构，易于扩展

**下一步**: 按照 task.md 中的任务清单，逐步完成各阶段开发。
