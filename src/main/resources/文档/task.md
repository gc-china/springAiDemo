# 企业级商业智能 Agent 项目任务清单

## 项目概述
基于 Spring AI 和 Spring Boot 构建的企业级智能仓储管理系统，目标是升级为与市面主流 Agent 产品（RAG + Tooling + Memory + LLM Ops）相匹配的工程化解决方案。

---

## 阶段一：基础设施固化与数据模型构建

### 1.1 会话记忆（Session Memory）
- ✅ Redis 会话存储层设计
  - ✅ 设计 conversationId 隔离机制
  - ✅实现 Redis List/Stream/Hash 存储结构
  - ✅ 实现消息 schema（id, role, text, tokens, metadata）
- ✅ 滑动窗口策略实现
  - ✅ 按 token 预算累加最近消息
  - ✅ 实现 max_prompt_tokens 限制
- [] 写入并发与顺序保障
  - [] 实现 Redis Stream 顺序写入
  - [] 配置 consumer group 持久化机制
- [ ] TTL、归档与冷存
  - ✅ 实现会话 TTL（7天可配置）
  - [ ] 异步归档到 PostgreSQL/S3
  - [ ] 实现归档引用记录
- [ ] 错误处理与 DLQ
  - [ ] 实现 Dead Letter Queue
  - [ ] 配置告警机制
  - [ ] 数据一致性校验
- [ ] 监控指标
  - [ ] Redis 写入延迟（p50/p95/p99）
  - [ ] Stream lag 监控
  - [ ] 归档失败率统计

### 1.2 向量知识库（PGVector）
- [ ] 数据库表结构设计
  - [ ] `document` 表（文档级元数据）
  - [ ] `document_chunk` 表（切片级记录）
  - [ ] 添加 chunk_hash、metadata JSONB 字段
- [ ] 切片策略实现
  - [ ] 实现 200-600 tokens 切片
  - [ ] 实现 50-150 tokens 重叠
  - [ ] 按自然段/句子切分
- [ ] Embedding 与批量插入
  - [ ] 批量生成 embedding
  - [ ] 批量写入数据库
  - [ ] chunk_hash 重复检测
- [ ] 索引与检索引擎
  - [ ] 配置 PGVector HNSW/IVFFLAT 索引
  - [ ] metadata 字段 GIN 索引
  - [ ] 实现租户/语言过滤
- [ ] 可追溯性
  - [ ] 添加 source_file、page、paragraph_index
  - [ ] RAG 结果引用来源
- [ ] 扩展策略
  - [ ] 表分区（按 tenant/时间）
  - [ ] read replicas 配置
  - [ ] 索引维护（VACUUM/REINDEX/ANALYZE）
- [ ] 备份与恢复
  - [ ] WAL 配置
  - [ ] 周期性 snapshot
- [ ] 监控指标
  - [ ] Ingest 吞吐量
  - [ ] embedding 失败率
  - [ ] 检索延迟（p95/p99）
  - [ ] 检索 recall 质量测试

---

## 阶段二：知识库自动化与高级 RAG

### 2.1 文档 Ingestion（异步 ETL）
- [ ] 异步 ETL 流程
  - [ ] 前端上传接口（返回 upload_id）
  - [ ] 消息队列集成（Kafka/RabbitMQ/Pulsar）
  - [ ] Worker 异步处理
  - [ ] 状态更新与通知（Webhook/SSE）
- [ ] 文档解析能力
  - [ ] Tika/Unstructured 集成
  - [ ] 表格提取
  - [ ] OCR 集成（confidence threshold）
- [ ] 切片质量控制
  - [ ] 语义完整性保证
  - [ ] 重叠设置优化
- [ ] 异步化与进度管理
  - [ ] status API 实现
  - [ ] SSE/Webhook 通知
- [ ] 重复与去重
  - [ ] 文件级 hash
  - [ ] chunk-level hash
  - [ ] 去重逻辑
- [ ] 错误与重试策略
  - [ ] 指数退避重试
  - [ ] 失败标记与原因记录
  - [ ] 人工重触发支持
- [ ] 可观测性
  - [ ] 阶段耗时跟踪
  - [ ] 失败率统计

### 2.2 混合检索与 Reranker
- [ ] 混合检索实现
  - [ ] 向量召回（Top N_vector）
  - [ ] 全文召回（Top N_text）
  - [ ] Reciprocal Rank Fusion 融合
- [ ] Reranker 集成
  - [ ] Reranker 服务部署
  - [ ] 二次精排逻辑
  - [ ] Top K 证据选择
- [ ] Prompt 注入与证据展示
  - [ ] 结构化证据注入
  - [ ] 引用来源要求
  - [ ] "未找到证据"处理
- [ ] 幻觉控制（Verifier）
  - [ ] Verifier 模块实现
  - [ ] 断言支持度判断
  - [ ] unsupported 处理
- [ ] 性能优化
  - [ ] 快速模式（Quick Mode）
  - [ ] 精准模式选择

---

## 阶段三：Agent 智能决策与工具增强

### 3.1 参数清洗层（Parameter Correction）
- [x] ParamNormalizer Pipeline（已实现 ArgumentCorrectionAspect）
  - [x] Normalization（字符串清理）
  - [x] Entity Resolution（模糊匹配到标准实体）
  - [x] Validation（类型与范围校验）
  - [x] Ambiguity Handling（多候选处理）
- [ ] 工具接口返回约定
  - [ ] 统一返回结构（ok|ambiguous|not_found|error）
  - [ ] payload 与 explain 字段
- [ ] 审计与可回溯
  - [ ] 参数解析链路保存

### 3.2 业务上下文与 Slot Filling
- [ ] CurrentTask 状态设计
  - [ ] task_id、type、status、slots 定义
  - [ ] context_meta 设计
- [ ] 存储策略
  - [ ] Redis 快速读写
  - [ ] Postgres 持久化审计
  - [ ] slot 更新日志
- [ ] Prompt 控制策略
  - [ ] current_state 注入
  - [ ] 缺失 slot 询问指示
- [ ] Handler 与状态转换
  - [ ] TaskHandler 实现
  - [ ] 状态转换规则
  - [ ] 超时策略
  - [ ] 补偿动作
- [ ] 跨系统事务与补偿（Saga）
  - [ ] Saga pattern 实现
  - [ ] 补偿动作定义
  - [ ] 异常回滚
- [ ] 幂等与重复请求
  - [ ] request_id/correlation_id
  - [ ] 幂等接口设计

---

## 阶段四：前端交互与安全认证

### 4.1 自定义 SSE 协议与思维链展示
- [ ] SSE 消息类型设计
  - [ ] type: thinking/content/tool/error/final/progress
  - [ ] stage、delta、seq、meta 字段
- [ ] 前端 UX 实现
  - [ ] 思维链卡片化
  - [ ] 流式渲染（Markdown）
  - [ ] 交互控制（取消/快速模式）
  - [ ] ambiguous 候选按钮
- [ ] SSE 鉴权
  - [ ] 短期 socket token
  - [ ] JWT 签名机制

### 4.2 用户鉴权与会话隔离
- [ ] 鉴权流程（网关层）
  - [ ] API Gateway JWT 校验
  - [ ] userId/tenantId 注入
- [ ] 多租户策略
  - [ ] 行级租户隔离
  - [ ] Schema/DB 级隔离（可选）
  - [ ] tenant_id 过滤
- [ ] 最小权限原则
  - [ ] RBAC/ABAC 实现
  - [ ] tool 调用权限校验
- [ ] 审计
  - [ ] 敏感操作日志（append-only）
  - [ ] trace-id 关联

---

## 阶段五：生产运维与成本控制（LLMOps）

### 5.1 语义缓存与降本策略
- [ ] 缓存设计
  - [ ] Redis/向量缓存实现
  - [ ] semantic hash/embedding bucket
  - [ ] 命中策略（完全/高相似度）
- [ ] 失效与更新
  - [ ] 知识库更新触发失效
  - [ ] 热点问题 pin 策略
- [ ] 模式选择
  - [ ] 快速模式（低成本）
  - [ ] 精准模式（高准确度）

### 5.2 安全围栏、监控与合规
- [ ] 输入输出防护
  - [ ] DFA/Regex 危险输入拦截
  - [ ] PII 掩码（身份证、银行卡等）
  - [ ] 输出 PII detector
- [ ] 监控与可观测
  - [ ] Prometheus 指标
  - [ ] Jaeger 链路追踪
  - [ ] Loki 结构化日志
- [ ] 告警与 SLO 管理
  - [ ] 成本告警（token usage）
  - [ ] 质量告警（hallucination 率）
  - [ ] 可用性告警
  - [ ] PagerDuty/OpsGenie 集成
- [ ] 合规与审计
  - [ ] 敏感数据访问日志
  - [ ] KMS 密钥管理
  - [ ] 数据保留期与删除接口

---

## 横切主题：CI/CD、Infra-as-Code 与灾备

### CI/CD 与发布策略
- [ ] GitOps/CI pipelines
  - [ ] build → test → canary → prod
- [ ] Canary 流量策略
  - [ ] 1-5% 流量验证
  - [ ] 平滑放量
- [ ] 自动回滚
  - [ ] 错误率/SLO 触发

### Infra-as-Code
- [ ] Terraform 管理
  - [ ] VPC、K8s、RDS、Redis、IAM
- [ ] Helm 管理
  - [ ] 应用部署与配置
- [ ] PR + review + pipeline

### 灾备
- [ ] 多可用区部署
- [ ] Postgres 主从复制与 WAL 备份
- [ ] Redis AOF/RDB 及 replicas
- [ ] 灾备演练
  - [ ] 主库故障模拟
  - [ ] 恢复验证
  - [ ] 数据一致性检查
  - [ ] RTO/RPO 验证

---

## SLA / SLO / 指标与告警

### SLA 定义
- [ ] 平均可用性：99.9%（月度）
- [ ] RAG-only 请求 P95 latency < 800ms
- [ ] 完整 LLM 流程 P95 latency < 3s
- [ ] 幻觉率（verifier fail） < 1%

### 核心 SLI
- [ ] request_success_rate
- [ ] p50/p95/p99 latency
- [ ] llm_token_usage_per_request
- [ ] cache_hit_rate
- [ ] retriever_recall

### 告警配置
- [ ] token usage 突增告警
- [ ] hallucination_rate 超阈值
- [ ] redis stream lag 超阈值
- [ ] PGVector search latency p99 超阈值

---

## 交付物清单

- [ ] OpenAPI 文档（chat/knowledge/tool/state）
- [ ] Postgres & PGVector schema 说明文档
- [ ] Redis Key 规范与 Stream 消费架构说明
- [ ] ETL pipeline 设计与运维说明
- [ ] RAG pipeline 设计文档
- [ ] Agent Orchestrator 设计文档
- [ ] 前端 Interaction Spec（SSE 协议、思维链 UX）
- [ ] Observability Dashboard 列表与告警清单
- [ ] Runbook（常见故障处理 & 回滚步骤）
- [ ] Security Checklist（PII 处理、KMS、审计）

---

## MVP 路线图（8-12 周）

### Week 1-2：基础 infra
- [ ] K8s、Redis、Postgres+PGVector 部署
- [ ] API Spec 定义
- [ ] DB schema 设计

### Week 3-4：文档 Ingestion
- [ ] 异步 ETL 实现
- [ ] 向量化入库
- [ ] 基本检索（向量召回）

### Week 5-6：Agent Orchestrator
- [ ] chat stream 实现
- [ ] session memory 实现
- [ ] RAG minimal 集成
- [ ] 前端 demo（SSE）

### Week 7-8：工具与 workflow
- [ ] 工具示例（inventory query）
- [ ] slot-filling workflow
- [ ] observability baseline

### Week 9-12：高级特性与加固
- [ ] Reranker 集成
- [ ] semantic cache 实现
- [ ] security hardening
- [ ] SLA 验证与演练

---

## 当前项目状态

### 已完成 ✅
- [x] Spring Boot 3.3.5 + Spring AI 1.0.0 基础框架
- [x] 工具自动发现与注册（ToolRegistry）
- [x] 参数矫正策略（ArgumentCorrectionAspect）
- [x] 人机确认策略（TransferRequest confirmed 字段）
- [x] 基础 RAG 服务（RagService）
- [x] 会话记忆（ChatMemory -> Redis Session Layer）
- [x] 流式响应（SSE）
- [x] Redis 会话存储层（SessionMemoryService）

### 进行中 🚧
- [ ] 向量知识库优化（当前使用 SimpleVectorStore）
- [ ] 混合检索与 Reranker
- [ ] 完整的监控与告警体系

### 待开始 ⏳
- [ ] PostgreSQL + PGVector 迁移
- [ ] PostgreSQL + PGVector 迁移
- [ ] 异步 ETL pipeline
- [ ] Slot Filling 状态机
- [ ] 前端思维链可视化
- [ ] 安全认证与多租户
- [ ] LLMOps 完整体系
