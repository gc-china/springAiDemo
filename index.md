# LLM 开发者学习路线图（详尽版）

## 前期（基础知识、1-2 周）
- **Python / Java / JS 基础**
  - 熟悉至少一种后端语言（推荐 Python 或 Java）
- **机器学习与深度学习基础**
  - 线性代数、概率论基础
  - 神经网络与优化（反向传播、梯度下降）
- **Transformer 与自注意力机制**
  - 阅读 "Attention is All You Need"
  - 理解 Encoder / Decoder / Self-attention

## 核心技术栈（2-4 周）
- **Tokenizer 与 Embeddings**
  - 子词分词（BPE / SentencePiece）
  - 向量表示、相似度度量（余弦、内积）
- **Prompting 基础**
  - System / User / Assistant prompt
  - Few-shot, Zero-shot，Chain-of-Thought
- **LLM 使用**
  - OpenAI / Anthropic / local models (e.g., Llama, Mistral)
  - 理解 Context Window, API usage与限额

## 中级（项目实战，4-8 周）
- **RAG 实战**
  - 文档切片（Chunking）、Embedding、向量库（Milvus、PGVector、Chroma）
  - 检索策略（Top-K、MMR）、重排序（Reranker）
  - Query rewriting 与多轮检索（Multi-hop）
- **工具调用（Function Calling）**
  - 结构化响应（JSON schema）
  - 设计可靠的 API Connectors
  - 幂等性与错误处理
- **对话与记忆**
  - Buffer memory、summary memory、vector memory
  - 记忆压缩、检索策略

## 高级（系统设计、优化，持续）
- **Agent 与自动化**
  - ReAct、Plan-and-Execute、Task decomposition
  - 长任务管理、循环执行、安全限制
- **推理优化**
  - Chain-of-Thought、Tree-of-Thought、Self-consistency
  - 使用 external tools（calculator, code-runner, python sandbox）辅助推理
- **模型微调与对齐**
  - SFT、LoRA、QLoRA（当资源允许）
  - RLHF / DPO 基本原理（了解即可）

## 工程化与运维
- **监控与评估**
  - 统计调用成功率、工具调用准确率、事实性检测
  - 日志化 prompt / response (红线敏感信息掩码)
- **成本控制**
  - Token 优化、cache、节流
- **安全**
  - Prompt injection 防护、权限边界、率限制

## 推荐实践项目（按难度）
1. 简单：基于 OpenAI 的问答机器人（含 prompt cache）
2. 中等：RAG 问答系统（向量库 + retriever + reranker）
3. 高级：Agent 平台（planner + tool router + multi-agent）
4. 研究：自研小型 LLM 调优（LoRA 微调 + 评估）

## 推荐学习资料
- 《Attention Is All You Need》
- OpenAI Cookbook、LangChain 文档
- Milvus / PGVector / Chroma 官方指南
- Papers: Chain-of-Thought, Tree of Thought, ReAct

## 工具与库清单
- LLM SDK: OpenAI, Anthropic, Cohere, Mistral (inference)
- Orchestration: LangChain, LlamaIndex, Spring AI
- Vector DB: Milvus, Pinecone, Weaviate, PGVector, Chroma
- Reranker / Retriever: sentence-transformers, DPR, BEIR datasets

---

**生成时间说明**：此路线图为实践导向，假设每周有 10-20 小时的学习时间。根据个人经验可在 2-4 个月掌握完整栈。