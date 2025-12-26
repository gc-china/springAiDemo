# 智能仓储系统：混合检索 (Vector + Keyword) 与 RRF 融合实施指南

> 本文档是一份**生产级技术白皮书**，详细阐述了如何通过“双路召回”和“重排序”技术，解决传统 RAG
> 系统中“查不到精确型号”或“幻觉严重”的核心痛点，实现企业级知识库的高精度检索。

---

## 📋 目录

1. [系统架构全景](#1-系统架构全景)
2. [小白也能懂的核心概念](#2-小白也能懂的核心概念)
3. [策略一：语义召回链路 (Vector)](#3-策略一语义召回链路-vector)
4. [策略二：精确召回链路 (Keyword)](#4-策略二精确召回链路-keyword)
5. [核心逻辑：RRF 融合与重排序](#5-核心逻辑rrf-融合与重排序)
6. [完整代码实现](#6-完整代码实现)
7. [实施步骤](#7-实施步骤)
8. [验证场景](#8-验证场景)

---

## 1. 系统架构全景

### 1.1 检索流程总览图

这张图展示了用户提问后，系统如何在**毫秒级**内并行调用两个“大脑”进行搜索，并像漏斗一样层层筛选出最佳答案。

```mermaid
graph TD
    User((用户))
    UserQuery[用户提问: "iPhone 15 库存"] 
    User --> UserQuery

    subgraph "双路召回层 (Parallel Retrieval)"
        direction TB
        UserQuery --> PathA[路径 A: 向量检索<br/>(理解语义/右脑)]
        UserQuery --> PathB[路径 B: 全文检索<br/>(精确匹配/左脑)]
        
        PathA --"Embeddings + PGVector"--> ListA[候选列表 A<br/>(Top 8)]
        PathB --"tsvector + GIN Index"--> ListB[候选列表 B<br/>(Top 8)]
    end

    subgraph "融合与精排层 (Fusion & Rerank)"
        ListA & ListB --> Mixer[RRF 混合器<br/>(数学算法融合)]
        Mixer --> Candidates[Top 16 候选集]
        
        Candidates --> Expert[LLM 评审专家<br/>(重排序模型)]
        Expert --"去伪存真"--> Final[Top 3 最终文档]
    end

    Final --> Generator[生成回答]
    Generator --> User
```

## 2. 小白也能懂的核心概念

为了让非算法背景的开发者也能理解，我们将复杂的混合检索比作**“图书馆找书”**：

### 2.1 为什么要“双路召回”？

#### 向量检索 (右脑/感性)

> **比喻**：你告诉图书管理员：“我想找一本关于‘虽然很累但还要坚持’的书。” 管理员给你《老人与海》。
>
> - **能力**：它懂语义，懂你的言外之意。
> - **缺点**：当你搜特定ID `X-2024-001` 时，它可能因为它“长得像” `X-2024-002` 而找错。

#### 全文检索 (左脑/理性)

> **比喻**：你告诉管理员：“我要找书号是 `978-7-115` 的书。” 管理员精准给你那一本。
>
> - **能力**：它懂精确匹配，绝不含糊。
> - **缺点**：你搜“大屏幕手机”，它找不到“iPhone Pro Max”，因为它不认为这两个词有关系。

### 2.2 什么是 RRF 融合？

> **痛点**：向量检索给出的分是 `0.85` (相似度)，全文检索给出的分是 `5.0` (词频)。这俩分数单位不一样，不能直接相加。

**RRF (Reciprocal Rank Fusion)**：

- **原理**：我不看分数，只看排名！
    - 如果一个文档在两边都排第 1，它的最终得分就极高。
    - 如果一个文档只在某一边出现，得分就中等。
- **作用**：它像一个公平的裁判，把两个不同体系的评分标准统一起来。

## 3. 策略一：语义召回链路 (Vector)

这是 RAG 的基础能力，利用大模型将文本转化为向量，负责“右脑”的模糊匹配。

- **实现方式**: 使用 `VectorStore.similaritySearch()`。
- **适用场景**:
    - 用户描述模糊：“我想找个屏幕大的设备”。
    - 跨语言搜索：用中文搜英文文档。

## 4. 策略二：精确召回链路 (Keyword)

这是本次升级的核心。我们利用 PostgreSQL 原生的全文检索能力，无需引入 ElasticSearch，保持架构轻量。

### 4.1 数据库层设计 (SQL)

我们需要给文档表加上“索引卡片” (`tsvector`) 和“快速目录” (`GIN Index`)。

```sql
-- 1. 添加向量列 (存储分词后的结果)
ALTER TABLE document_chunk
    ADD COLUMN IF NOT EXISTS content_search_vector tsvector;

-- 2. 创建 GIN 倒排索引 (核心加速器，支持毫秒级查询)
CREATE INDEX IF NOT EXISTS idx_document_chunk_content_search ON document_chunk USING GIN(content_search_vector);

-- 3. 配置自动更新触发器 (写入数据时自动分词)
CREATE
OR
REPLACE FUNCTION document_chunk_tsvector_trigger() RETURNS trigger AS $$
BEGIN
-- 'simple' 为基础分词，生产环境推荐使用 'jieba' (需安装 pg_jieba 插件)
    NEW.content_search_vector := to_tsvector('simple', NEW.content);
RETURN NEW;
END
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS tsvectorupdate ON document_chunk;
CREATE TRIGGER tsvectorupdate
    BEFORE INSERT OR UPDATE
    ON document_chunk FOR EACH ROW
EXECUTE PROCEDURE document_chunk_tsvector_trigger();
```

## 5. 核心逻辑：RRF 融合与重排序

### 5.1 RRF 算法公式

```
Score(d) = Σ (1 / (k + rank(d, r)))
```

*(其中 `k` 通常取 60，`rank` 为文档在列表中的排名)*

### 5.2 LLM Rerank (专家评审)

RRF 虽然好，但无法判断内容是否真的相关。我们把 RRF 选出的前 10-16 个文档，打包发给 LLM：
> "用户问了这个问题，这里有 10 段话，请你像专家一样挑出最有用的 3 段，只告诉我编号。"

这能有效去除“包含关键词但内容无关”的噪音（例如：用户搜“苹果”，系统找出了“苹果公司发布的耳机”，虽然有关键词，但不是水果）。

## 6. 完整代码实现

### 6.1 Mapper 层 (`DocumentChunkMapper.java`)

利用 MyBatis 执行原生 SQL 查询。

### 6.2 Service 层 (`RagService.java`)

实现双路并行召回与融合逻辑。

## 7. 实施步骤

1. **数据库升级**: 执行上述 SQL 脚本，为 `document_chunk` 表添加 `tsvector` 列和 `GIN` 索引。
2. **验证**: 执行 `EXPLAIN SELECT ...` 确认走了索引扫描。
3. **代码集成**:
    - 添加 `DocumentChunkMapper` 接口。
    - 升级 `RagService`，注入 Mapper 并替换原有的单一检索逻辑。
4. **Prompt 配置**: 确保 `src/main/resources/static/rerank-prompt.st` 文件存在，用于指导 LLM 进行重排序。

## 8. 验证场景

### 场景 1: 精确型号查询 (Keyword 主导)

- **用户**: "iPhone 15 Pro Max 的屏幕尺寸是多少？"
- **现象**:
    - **向量路**: 可能返回 iPhone 14 或普通 iPhone 15 的信息 (语义相近)。
    - **关键词路**: 强行匹配 "iPhone 15 Pro Max"。
- **结果**: RRF 融合后，包含精确型号的文档排名上升，LLM 最终锁定正确参数。

### 场景 2: 模糊意图查询 (Vector 主导)

- **用户**: "有没有适合打游戏的手机？"
- **现象**:
    - **关键词路**: 可能因为文档里没写“打游戏”三个字而挂零。
    - **向量路**: 匹配到“高性能”、“高刷屏”、“散热好”的手机文档。
- **结果**: 向量路结果主导 RRF 排名，系统成功推荐游戏手机。

### 场景 3: 干扰信息过滤 (Rerank 主导)

- **用户**: "苹果库存"
- **现象**: 召回了“苹果库存 50 台” (正解) 和 “员工食堂采购了苹果” (噪音)。
- **结果**: LLM 在 Rerank 阶段识别出用户意图是查询电子产品，自动剔除食堂采购记录，只保留库存数据。
