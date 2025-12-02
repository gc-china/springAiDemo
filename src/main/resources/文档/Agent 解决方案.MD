# 企业级商业智能 Agent 解决方案（市场化、工程级 · 完整版）

> 目标：将 Agent 方案升级为与市面主流 Agent 产品（RAG + Tooling + Memory + LLM Ops）相匹配的、可直接工程化落地的详细设计文档。本版为**纯方案**（去除代码示例），便于直接复制到项目文档或 PR 中。

---

## 目录

1. [总体架构与设计原则](#总体架构与设计原则)
2. [阶段一：基础设施固化与数据模型构建（详解）](#阶段一基础设施固化与数据模型构建详解)
3. [阶段二：知识库自动化与高级 RAG（详解）](#阶段二知识库自动化与高级-rag详解)
4. [阶段三：Agent 智能决策与工具增强（详解）](#阶段三agent智能决策与工具增强详解)
5. [阶段四：前端交互与安全认证（详解）](#阶段四前端交互与安全认证详解)
6. [阶段五：生产运维与成本控制（LLMOps）](#阶段五生产运维与成本控制-llmops)
7. [横切主题：CI/CD、Infra-as-Code 与灾备](#横切主题-cicdinfra-as-code-与灾备)
8. [SLA / SLO / 指标与告警清单（模板）](#sla--slo--指标与告警清单模板)
9. [交付物清单 & MVP 路线图（可执行）](#交付物清单--mvp-路线图可执行)
10. [常见风险与缓解策略速查表](#常见风险与缓解策略速查表)
11. [附录：接口契约与消息格式说明（概念性）](#附录接口契约与消息格式说明概念性)

---

# 总体架构与设计原则

**一句话概述**
构建一个以大模型为核心、数据驱动、工具可编排（orchestratable）、并具备可观测/可审计/可降级能力的认知型 Agent 平台，面向企业级多租户生产场景。

**整体组件**

* API Gateway（鉴权、流量控制）
* Agent Orchestrator（Spring Boot + Spring AI）：意图识别、流程编排、Tool 路由、流式 API 封装
* 会话记忆（Redis：List/Stream/Hash + 归档到 PG/S3）
* 向量知识库（Postgres + PGVector）与 Metadata 存储（JSONB）
* 模型服务（DashScope / Qwen 等，做推理和 embedding）
* RAG 子系统（Retriever / Fusion / Reranker / Verifier）
* Tooling 层（调用 ERP/Inventory/Payments 等后端系统）
* 前端（Vue 3 + Vite + Pinia，SSE 流式渲染与思维链可视化）
* Observability（Prometheus / Grafana / Jaeger / Loki）
* Infra（Kubernetes + Helm + Terraform）

**核心设计原则**

* 契约优先：OpenAPI/Protobuf，接口版本化与向后兼容。
* 可观测与可追溯：Trace-id 全链路贯穿，所有关键步骤打点并可回溯。
* 防御式设计：输入校验 → 熔断 → 重试 → 降级路径。
* 安全与合规：默认 TLS、KMS 加密、PII 掩码、最小权限。
* 成本可控：语义缓存、模型分层、token budget、按租户限额。

---

# 阶段一：基础设施固化与数据模型构建（详解）

### 目标

建立高可用的会话记忆层、可扩展的向量知识库，并把基础的监控与备份策略落实到位，为 RAG 与多轮对话奠定工程化基础。

## 1.1 会话记忆（Session Memory）—— 详细设计要点

**存储选择与职责分层**

* Redis（主力）：低延迟读写，承担会话短期记忆、滑动窗口、Stream 顺序化。
* PostgreSQL / S3（归档）：长期持久化、合规审计、训练数据采样。

**Key/对象设计（规范）**

* 会话历史（List 风格）：以 conversationId 为主键隔离，不同用户和对话使用独立 conversationId。
* 元信息（Hash）：保存 userId、lastActiveAt、ttl、status、pinnedSources 等。
* 顺序化写入（Stream）：使用 Redis Stream 或等价机制保证写入顺序与重放能力。
* 分布式锁（仅必要时）：用于保证关键场景中的顺序一致性。

**Message schema（约定字段）**

* id、role（user/assistant/tool/system）、text、tokens、embedding_ref、created_at、metadata（来源、tool 等）

**滑动窗口策略**

* 在注入 LLM 前，按 token 预算从最近消息向前累加，直至达到 max_prompt_tokens 或达到 message 数量上限，保证 prompt 大小稳定且包含最相关上下文。

**写入并发与顺序保障**

* 推荐写入到 Redis Stream，consumer group 负责将事件持久化到 List / DB，从而确保顺序和可重放。
* 若使用锁，采用成熟 RedLock 或集群式锁方案并注意性能权衡。

**TTL、归档与冷存**

* 活跃会话 TTL（例如 7 天，可配置）。到期或满足归档条件时异步将 Redis 中数据压缩并存入 S3 或写入 PG（JSONL），并在 Redis 中保留引用记录。

**错误处理与 DLQ**

* 写入失败或处理异常记录到 Dead Letter Queue 并触发告警/人工介入机制。
* 定期校验 Redis 与归档数据一致性，自动补救或告警。

**必备监控指标**

* Redis 写入延迟（p50/p95/p99）、Stream lag、归档失败率、token 聚合错误数。

---

## 1.2 向量知识库（PGVector）—— 详细设计要点

**表结构与 Metadata 要求（概念性）**

* `document`：文档级元数据（id、title、source、tenant）
* `document_chunk`：切片级记录（chunk_text、chunk_tokens、embedding、metadata JSON、chunk_hash、created_at）

**切片策略（语义优先）**

* 推荐 chunk 大小为 200–600 tokens，重叠 50–150 tokens。切片优先按自然段或句子切分，以保持语义完整，减少检索噪音。

**Embedding 与批量插入**

* 批量生成 embedding（批大小根据资源与吞吐设定），批量写入数据库以降低连接与事务开销。保存 chunk_hash 用于重复检测。

**索引与检索引擎**

* 使用 PGVector 的 HNSW / IVFFLAT 索引进行向量检索。对 metadata 字段建立 GIN 索引以支持元数据过滤（如 tenant、language、document_id）。

**可追溯性**

* 每个 chunk 的 metadata 必须包含 source_file、page、paragraph_index、extract_method 等可溯源信息。RAG 结果必须带上引用来源（文件名与位置），便于审计和给用户展示证据。

**扩展策略**

* 随数据增长采用表分区（按 tenant 或时间），并使用 read replicas 缓解查询压力。定期维护索引（VACUUM / REINDEX / ANALYZE）以保证性能。

**备份与恢复**

* 对 metadata 与索引进行常规备份，embedding 含量大的数据可选择冷存或在灾备策略中重建。采用 WAL 和周期性 snapshot 保护数据。

**必备监控指标**

* Ingest 吞吐、embedding 失败率、检索延迟（p95/p99）、索引大小、检索 recall 质量测试。

---

# 阶段二：知识库自动化与高级 RAG（详解）

### 目标

实现自动化 ETL（文档入库）、混合检索（向量 + 文本）与精排机制（reranker），显著降低 LLM 幻觉并为回答提供引用证据。

## 2.1 文档 Ingestion（异步 ETL）—— 要点

**总体流程**

1. 前端或系统上传文档（同步返回 upload_id）
2. API 保存 metadata 并将任务投递到消息队列（Kafka/RabbitMQ/Pulsar）
3. 后端 Worker 异步执行解析 → 切片（TokenTextSplitter）→ embedding 批量请求 → 插入 PGVector
4. 更新状态并推送通知（Webhook/SSE）

**解析能力**

* 普通文本与结构化文档使用 Tika/Unstructured。
* 表格优先提取结构化表格（同时保留图像与坐标）。
* 扫描件或图片走 OCR（设置 confidence threshold），对低置信度结果标记人工质检。

**切片质量控制**

* 切片优先以段落或句子为单位，尽量保持语义完整，避免在中间截断重要事实。重叠设置保证上下文连接。

**异步化与进度管理**

* 上传接口立即响应，后端异步处理并通过 status API 或 SSE/Webhook 通知进度和错误详情。避免前端长时间等待。

**重复与去重**

* 使用文件级 hash 结合 chunk-level hash 避免重复入库。对于已存在的 chunk，只更新来源或 metadata 而非重复插入。

**错误与重试策略**

* 临时失败使用指数退避重试（限定次数），不可恢复错误标记为 failed 并记录原因，支持人工重触发。

**可观测性**

* 跟踪每个文档从 upload → parse → slice → embed → insert 的阶段耗时与失败率，作为入库质量指标。

---

## 2.2 混合检索（Vector + Full-Text）与 Reranker（要点）

**为何做混合检索**

* 向量检索擅长语义匹配，对自然语言查询效果好；全文检索（FTS）对精确短语、编号、表格、数字更可靠。混合召回能显著提升相关性并减少误导信息。

**检索流程（并行召回与融合）**

1. 并行执行向量召回（Top N_vector）与全文召回（Top N_text）。
2. 将两套候选集合合并，使用融合算法（如 Reciprocal Rank Fusion）计算合并得分。
3. 将合并后的 Top M 候选交给 Reranker（二次精排），最终返回 Top K 证据注入到 LLM prompt。

**Reranker 角色与部署**

* Reranker 通常是一个小型模型或轻量 transformer，用于对候选证据的相关性与事实覆盖度做精细打分。建议做为独立服务部署以便独立扩缩容。

**Prompt 注入与证据展示**

* 将精排后的 Top K 证据以结构化格式注入 Prompt（Evidence 1/2/...），并在提示中要求 LLM 严格基于证据回答与引用来源。若 LLM 无法在证据中找到答案，应明确返回“未找到支持证据”的措辞。

**幻觉控制（Verifier）**

* 添加 Verifier 模块：在 LLM 给出答案后用规则或小模型判断答案中关键断言是否能被 evidence 支持。若 verifier 认为断言 unsupported，则返回“找不到证据”或请求进一步信息，而不是自由发挥。

**性能优化策略**

* 对低延迟场景提供快速路径（Quick Mode）：只做向量召回 Top 5 并跳过 reranker（精度降低但响应快）。供用户选择：快速/精准两种模式。

---

# 阶段三：Agent 智能决策与工具增强（详解）

### 目标

实现鲁棒的工具调用链、槽位填充（Slot Filling）状态管理与跨系统事务（幂等与补偿），确保业务流程可控且可审计。

## 3.1 参数清洗层（Parameter Correction）—— 要点

**设计理念**

* 工具必须对外暴露契约化接口，并在内部做参数校验与归一化，不能将模糊的 LLM 输出直接用于内部外部系统调用。

**ParamNormalizer Pipeline（通用步骤）**

1. Normalization：清理字符串、统一单位、标准化编码。
2. Entity Resolution：模糊匹配描述到标准实体（如产品名 -> SKU），使用倒排索引、trigram、相似度算法。
3. Validation：类型与范围校验（日期格式、金额范围等）。
4. Ambiguity Handling：若多个候选，返回 standardized candidates 并标注置信度。

**工具接口返回约定**

* 工具调用返回统一结构：状态（ok|ambiguous|not_found|error）、payload（执行结果或候选列表）、explain（参数转化与决策链）。Agent 根据状态决定后续行为（直接执行 / 反问用户 / 建议替代）。

**审计与可回溯**

* 保存参数解析链路（原始输入 -> 每步转换 -> 最终 ID），便于复盘故障与纠偏。

---

## 3.2 业务上下文与 Slot Filling（State Machine）

**CurrentTask（任务状态）设计要点**

* 将每个需要多步收集信息的业务（退货、预约、投诉等）抽象为 Task，Task 包含 task_id、type、status、slots（每个 slot 指明是否必须、收集状态、验证规则）和 context_meta。

**存储策略**

* Redis 用于快速读写与并发更新；Postgres 做持久化审计。每次 slot 更新都持久写日志以便回溯。

**Prompt 控制策略**

* 在每次交互调用 LLM 前注入 current_state（含已收集与缺失 slots），并指示 LLM 只询问一个明确缺失的 slot，保证对话方向清晰且可由系统解析为结构化结果。

**Handler 与状态转换**

* 为每类 Task 编写 TaskHandler，定义状态转换规则、校验逻辑、超时策略与补偿动作。TaskHandler 可在 slot 全部收集完毕后触发工具（例如退款接口）并记录 saga id。

**跨系统事务与补偿（Saga）**

* 对于需要多个系统协同的事务（退款、库存回补等），采用 Saga pattern：每一步提供补偿动作，并在异常时执行补偿，保持业务一致性与可回滚能力。

**幂等与重复请求**

* Tool 接口设计幂等（使用 request_id/correlation_id）以防止重复执行关键操作（如二次退款）。

---

# 阶段四：前端交互与安全认证（详解）

### 目标

提供流畅的实时用户体验（思维链可视化与 SSE 流式渲染），并在协议层面与业务层面实现鉴权与租户隔离。

## 4.1 自定义 SSE 协议与思维链展示

**SSE 消息类型设计（概念）**

* type：thinking / content / tool / error / final / progress
* stage：检索 / rerank / tool:{name} / llm:answer 等
* delta：流式文本片段或进度信息
* seq：消息序列号（前端可用来保证顺序）
* meta：可选，如 token 使用、cost estimate、sources

**前端 UX 建议**

* 思维链卡片化：每个 stage 作为可折叠卡片展示，展开可看 raw evidence、tool 调用输入输出与日志。
* 流式渲染：content 类型作为部分增量渲染（Markdown 支持），提升响应感。
* 交互控制：在 thinking 阶段用户可以“取消”或切换快速模式；当 Agent 返回 ambiguous 时提供候选按钮供一键确认。

**SSE 鉴权**

* 建议在握手时使用短期 socket token（后端基于 JWT 颁发短期签名），避免将长期 JWT 放在 URL。

---

## 4.2 用户鉴权与会话隔离

**鉴权流程（网关层）**

* API Gateway 校验 JWT（签名、过期、scope），并将 userId / tenantId 注入到下游请求头或 Security Context。

**多租户策略**

* 支持两种策略（可选）：

  * 行级租户隔离（每条数据带 tenant_id）—— 适合多数 SaaS 场景；
  * Schema/DB 级隔离（高隔离）—— 适合大型客户或合规要求高的场景。
* 在检索与存储时强制使用 tenant_id 或 schema 过滤。

**最小权限原则**

* Tooling 与后端 API 采用 RBAC（基于角色的权限）或 ABAC（属性基的访问控制）校验。每个 tool 调用前都做权限校验。

**审计**

* 所有敏感操作（如退款、导出、访问 PII）记录审计日志（append-only），包含 actor、time、params、result 与 trace-id，支持查询与法务审计。

---

# 阶段五：生产运维与成本控制（LLMOps）

### 目标

保证平台长期稳定与安全运行，并通过手段控制大模型调用成本。

## 5.1 语义缓存（Semantic Cache）与降本策略

**缓存设计**

* 在 Redis 或专用向量缓存中保存常见问答对（key: semantic hash or embedding bucket -> value: answer + sources + embedding + timestamp）。
* 命中策略：完全命中（query hash）或高相似度命中（embedding similarity > threshold，如 0.98）可直接返回 cached answer。

**失效与更新**

* 当知识库发生更新（document ingest/refresh）时发事件触发相关缓存失效（可按 document_id 或标签级别失效）。
* 热点问题支持 pin（长期缓存）策略。

**模式选择**

* 快速模式（低成本）：优先命中缓存并只做 minimal retriever。
* 精准模式（高准确度）：完整 pipeline（retriever + reranker + verifier），但成本与延时更高。可供不同用户/场景选择。

## 5.2 安全围栏、监控与合规

**输入输出防护**

* 输入清洗：DFA/Regex 层拦截 shell 注入、外部命令、URL 执行样式等危险输入。
* PII 掩码：自动检测并对敏感字符串（身份证号、银行卡、手机号、邮箱等）进行掩码或拒绝。
* 输出审查：每次 LLM 输出通过 PII detector 与规则校验，发现敏感信息则 redact 并记录安全事件。

**监控与可观测**

* 指标（Prometheus）：请求量、成功率、latency（p50/p95/p99）、LLM token usage、cache hit rate、retriever recall、hallucination rate（verifier fail%）等。
* Trace（Jaeger）：链路追踪覆盖 RAG、reranker、tooling、model 调用。
* 日志（Loki）：结构化日志（JSON），包含 trace-id 与关键信息。

**告警与 SLO 管理**

* 设置成本告警（token usage 异常）、质量告警（hallucination 率增高）、可用性告警（redis/pg 无法访问或延迟恶化）等，并通过 PagerDuty/OpsGenie 通知值班人员。

**合规与审计**

* 记录所有敏感数据访问日志，采用 KMS 管理密钥并审计访问。
* 为合规客户提供数据保留期与删除接口（遵从并记录删除操作）。

---

# 横切主题：CI/CD、Infra-as-Code 与灾备

**CI/CD 与发布策略**

* 推荐 GitOps 或 CI pipelines（build → test → canary → prod）。
* Canary 流量策略（默认 1–5%）用于线上验证后平滑放量。
* 自动回滚基于错误率/SLO。

**Infra-as-Code**

* 使用 Terraform 管理云资源（VPC、K8s、RDS、Redis、IAM）。
* 使用 Helm 管理应用部署与配置。
* 所有 infra 变更走 PR + review + pipeline。

**灾备**

* 多可用区部署关键组件，Postgres 使用主从复制与 WAL 备份，Redis 使用 AOF/RDB 及 replicas。
* 定期灾备演练（模拟主库故障、恢复、数据一致性检查），确保满足 RTO/RPO 要求。

---

# SLA / SLO / 指标与告警清单（模板）

**示例 SLA（可调整）**

* 平均可用性：99.9%（月度）
* RAG-only 请求 P95 latency < 800ms
* 完整 LLM 流程 P95 latency < 3s
* 幻觉率（verifier fail） < 1%

**核心 SLI（需持续监控）**

* request_success_rate
* p50/p95/p99 latency
* llm_token_usage_per_request
* cache_hit_rate
* retriever_recall（定期 QA 测试）

**示例告警（必须）**

* token usage 突增（短期内超出 baseline × 2）
* hallucination_rate 超阈值
* redis stream lag 超阈值
* PGVector search latency p99 超阈值

---

# 交付物清单 & MVP 路线图（可执行）

**交付物**

1. OpenAPI 文档（chat / knowledge / tool / state）
2. Postgres & PGVector schema 说明文档
3. Redis Key 规范与 Stream 消费架构说明
4. ETL pipeline 设计（异步消息队列）与运维说明
5. RAG pipeline 设计（retriever + fusion + reranker + verifier）
6. Agent Orchestrator 设计文档（接口契约、state machine、工具框架）
7. 前端 Interaction Spec（SSE 协议、思维链 UX、权限）
8. Observability Dashboard 列表与告警清单
9. Runbook（常见故障处理 & 回滚步骤）
10. Security Checklist（PII 处理、KMS、审计、合规）

**MVP 路线图（建议 8–12 周）**

* Week 1–2：基础 infra（K8s、Redis、Postgres+PGVector）、API Spec、DB schema
* Week 3–4：文档 Ingestion（异步）、向量化入库、基本检索（向量召回）
* Week 5–6：Agent Orchestrator（chat stream、session memory、RAG minimal） & 前端 demo（SSE）
* Week 7–8：工具示例（inventory query）、slot-filling workflow、observability baseline
* Week 9–12：Reranker、semantic cache、security hardening、SLA 验证与演练

---

# 常见风险与缓解策略速查表

* 风险：LLM 幻觉率高

  * 缓解：增加 reranker、evidence-first prompt、verifier、严格引用策略

* 风险：调用成本飙升

  * 缓解：语义缓存、模型分层（小模型缓存/大模型推理）、token budget、流量限额

* 风险：会话乱序或丢失

  * 缓解：使用 Redis Stream、持久化归档、写入 DLQ、stream lag 监控

* 风险：数据泄露（PII）

  * 缓解：入参/出参掩码、KMS 管理、审计日志、权限控制与审计

* 风险：检索性能瓶颈

  * 缓解：表分区、read replicas、index 调优、reranker 缓存

---

# 附录：接口契约与消息格式说明（概念性）

**会话写入（概念）**

* 入参包含 conversationId、message（role、text、metadata）、jwt 等。后端写入 Redis Stream/ List 并返回操作状态与 trace-id。

**历史查询（概念）**

* 支持按 conversationId 拉取最近 N 条消息，或按时间范围分页查询；提供归档链接以获取历史更久的数据。

**知识上传（概念）**

* 上传返回 upload_id；后端异步处理并提供 status 查询接口与回调/事件通知。

**RAG & Answer 流（概念）**

* 前端发起 query（含 conversationId、mode：quick/precise、tenantId）→ Agent orchestrator 执行：memory slice → retriever（vector+fts）→ fusion → reranker → build prompt（with evidence）→ model call → verifier → SSE 流式输出（thinking/content/final）并在 final 中带上 sources。

**SSE 消息（概念）**

* 每条消息为 JSON，包含 type、stage、delta、seq、meta（可选）。前端按 seq 保证渲染顺序与去重。

---
