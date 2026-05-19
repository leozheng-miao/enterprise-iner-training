# 阶段 1：RAG 核心 设计文档

> 项目：行业研报多 Agent 协作平台 — 阶段 1
> 上游依赖：阶段 0 已交付（Security/JWT、MySQL/Redis/RocketMQ/PGVector/ES 基础设施、注册登录链路、Knife4j）
> 设计日期：2026-05-19
> 工期估算：**7-10 天**

---

## Context（为什么做这个阶段）

阶段 0 完成了**鉴权骨架与基础设施**，但项目还不能"做事"。阶段 1 要让平台具备**第一项业务能力 —— 知识检索**：

- 把预置的行业研报 PDF 入库为可检索的知识
- 暴露 `/api/rag/search` 接口，输入主题、返回带引用的相关片段
- 这是后续阶段（Agent 编排、研报生成）的底座 —— Researcher Agent 调用的 `hybrid_search` 工具就来自本阶段

简历亮点目标：
> "Hybrid Search (PGVector + ES BM25) + 应用层 RRF 融合 + 阿里云 gte-rerank 重排，使 50 条评估集上 Recall@10 从 0.62（仅向量）提升至 0.89，Rerank 使 NDCG@10 进一步提升 0.10+。"

---

## §1 决策汇总（来自 brainstorming）

| 维度 | 选定方案 | 理由 |
|---|---|---|
| 语料源 | 信通院/部委白皮书/企业财报，30-50 篇 PDF | 完全合规、免费、专业；可复现 |
| PDF 解析 | Spring AI `ParagraphPdfDocumentReader` + `TokenTextSplitter` 兜底 | 结构感知（按 outline 切章节，保留 section_title），同时避免自写 PDFBox 规则的复杂度 |
| Embedding（MVP）| 阿里云 `text-embedding-v3`（1024d） | 与 DashScope key 复用，零本地资源，Spring AI 原生对接 |
| Embedding（阶段 6）| 本地 BGE-large-zh-v1.5 via Ollama | A/B 对比 → 简历讲故事 |
| 向量库 | PGVector + HNSW (cosine) | 阶段 0 已起、Spring AI 一等公民支持 |
| 关键字 | Elasticsearch 8 + IK 中文分词器 | 阶段 0 已起、中文 BM25 必备 |
| Hybrid | 应用层 RRF (k=60) | 实现简单、不依赖任一引擎特性 |
| Rerank | 阿里云 `gte-rerank-v2` | 与 Embedding 同一 API key，中文 SOTA |
| 评估数据集 | LLM 合成 ~100 query + 人工筛选 → 50 条 GT | 平衡质量与工作量 |
| 评估指标 | Recall@10、MRR、NDCG@10、Rerank 前后对比 | RAG 业界标准 |

### 务实边界（YAGNI）

| 砍掉 | 替代 | 理由 |
|---|---|---|
| ❌ 多模态 PDF 解析（图表/公式/表格） | ✅ 文本提取 + 章节标题 | 工程量爆炸，阶段 1 不必要 |
| ❌ 增量索引 / 实时 ingest | ✅ 一次性离线 ingest（CLI 触发） | 阶段 1 语料是预置的，不需要在线增量 |
| ❌ 多语言（英文文档） | ✅ 仅中文 | 语料源都是中文 |
| ❌ 用户上传 PDF 接口 | ✅ 预置语料 + ingest 脚本 | 阶段 1 不需要 UI；阶段 4 平台中台再考虑 |
| ❌ 完整 query 改写 | ✅ 直接用原 query | Query 改写是阶段 5 微调模块的事 |

---

## §2 端到端流程

```
┌─────────────────────────────────────────────────────────────────┐
│ Ingest Pipeline （离线，一次性 / 触发式）                         │
│                                                                  │
│ src/main/resources/corpus/*.pdf                                  │
│   │                                                              │
│   ▼                                                              │
│ ParagraphPdfDocumentReader                                       │
│   按 PDF outline (TOC) 切章节，每章节一个 Document               │
│   metadata: {doc_id, doc_title, section_title, page_start/end}   │
│   │                                                              │
│   ▼                                                              │
│ TokenTextSplitter (chunk=800, overlap=100)                       │
│   章节 > 800 tokens 时进一步切分                                  │
│   │                                                              │
│   ▼                                                              │
│ EmbeddingClient (text-embedding-v3)                              │
│   batch=25，每批 1 次 HTTP                                        │
│   │                                                              │
│   ▼              ▼                ▼                              │
│ PGVector       ES (IK)          MySQL                            │
│  vector(1024)   text+keyword     knowledge_doc / knowledge_chunk │
│                                  （元数据 + 引用追踪）             │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│ Search Pipeline (在线，<2s P95)                                   │
│                                                                  │
│ GET /api/rag/search?q=具身智能产业链&topK=10                       │
│   │                                                              │
│   ├─ Virtual Thread 1: VectorSearch (PGVector) → Top-50          │
│   └─ Virtual Thread 2: BM25Search (ES + IK) → Top-50             │
│                          │                                       │
│                          ▼                                       │
│              RrfFusion (k=60)  → Top-30 候选                     │
│                          │                                       │
│                          ▼                                       │
│              GteReranker (阿里云 gte-rerank-v2) → Top-K          │
│                          │                                       │
│                          ▼                                       │
│              带引用的片段 (doc_id, page_range, section_title)    │
└─────────────────────────────────────────────────────────────────┘
```

---

## §3 数据模型（追加到 MySQL）

继承 `BaseEntity`，自动获得 id/create_time/update_time/is_deleted。

### `knowledge_doc`（文档元数据）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| title | VARCHAR(255) | 文档标题（PDF metadata 取，缺省取文件名）|
| source | VARCHAR(64) | 来源机构：CAICT/MIIT/NDRC/CCID/COMPANY-XXX |
| file_uri | VARCHAR(512) | 原始 PDF 相对路径（corpus/xxx.pdf） |
| total_pages | INT | |
| content_hash | CHAR(64) | SHA-256，去重用 |
| ingested_at | DATETIME | |
| create_time / update_time / is_deleted | | BaseEntity |
| UNIQUE KEY | (content_hash) | 防止重复 ingest |

### `knowledge_chunk`（chunk 轻量元数据）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| doc_id | BIGINT FK | → knowledge_doc.id |
| section_title | VARCHAR(255) | 章节标题（从 outline 取） |
| page_start / page_end | INT | 引用追踪 |
| char_len | INT | |
| vector_id | UUID | PGVector 主键，便于 join |
| es_doc_id | VARCHAR(64) | ES `_id`，便于 join |
| create_time / update_time / is_deleted | | BaseEntity |
| INDEX | (doc_id), (vector_id), (es_doc_id) | |

> **Chunk 文本本体不放 MySQL**：放 PGVector 与 ES 即可。MySQL 只放轻量元数据。检索时从 PGVector/ES 直接拿文本 + 命中 score。

### `rag_eval_query`（评估数据集）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| query_text | VARCHAR(512) | |
| gold_chunk_ids | JSON | 标准答案的 chunk_id 列表（来自合成 / 人工标）|
| query_source | VARCHAR(16) | synthesized / human |
| create_time / update_time / is_deleted | | BaseEntity |

---

## §4 PGVector / ES 模式

### PGVector（数据库 `irp_vec`，已通过 docker-compose 起）

```sql
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS knowledge_chunk_vec (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chunk_id     BIGINT NOT NULL,           -- 对应 MySQL knowledge_chunk.id
    doc_id       BIGINT NOT NULL,
    section_title TEXT,
    page_start   INT,
    page_end     INT,
    content      TEXT NOT NULL,             -- chunk 原文（检索时返回）
    embedding    vector(1024) NOT NULL,
    created_at   TIMESTAMP DEFAULT now()
);

CREATE INDEX ON knowledge_chunk_vec USING hnsw (embedding vector_cosine_ops);
CREATE INDEX ON knowledge_chunk_vec (chunk_id);
CREATE INDEX ON knowledge_chunk_vec (doc_id);
```

### ES 8 index `knowledge_chunk_bm25`

```json
{
  "settings": {
    "analysis": {
      "analyzer": {
        "ik_max_word_with_pinyin": { "type": "custom", "tokenizer": "ik_max_word" }
      }
    }
  },
  "mappings": {
    "properties": {
      "chunk_id":      { "type": "long" },
      "doc_id":        { "type": "long" },
      "section_title": { "type": "text", "analyzer": "ik_max_word" },
      "content":       { "type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart" },
      "page_start":    { "type": "integer" },
      "page_end":      { "type": "integer" }
    }
  }
}
```

---

## §5 关键接口与契约

### REST API

| Method | Path | 说明 | 鉴权 |
|---|---|---|---|
| POST | `/api/rag/ingest` | 触发离线 ingest（指定 corpus 目录或全量重建）| JWT (ADMIN) |
| GET  | `/api/rag/search?q=&topK=&useRerank=` | Hybrid 检索 + Rerank | JWT (USER) |
| POST | `/api/rag/eval/run` | 触发评估，跑 50 条 GT 算 Recall@k/MRR/NDCG | JWT (ADMIN) |
| GET  | `/api/rag/eval/latest` | 最近一次评估结果 | JWT (USER) |

### `/api/rag/search` 响应

```json
{
  "code": 0,
  "data": {
    "query": "具身智能产业链",
    "tookMs": 1180,
    "hits": [
      {
        "chunkId": 4521,
        "score": 0.87,
        "rerankScore": 0.94,
        "content": "...",
        "citation": {
          "docId": 31,
          "docTitle": "2026 中国具身智能产业发展白皮书",
          "source": "CAICT",
          "sectionTitle": "第三章 产业链上中下游",
          "pageStart": 23,
          "pageEnd": 27
        }
      }
    ]
  },
  "message": "ok"
}
```

---

## §6 评估方法

### 评估数据集构建

1. **合成阶段（一次性脚本）**：用 qwen-max 对每篇文档采样 3 个章节，每个章节合成 1 个 "用户可能问的检索 query"。共得到 ~100-150 条 (query, gold_chunk_id) 对。
2. **人工筛选**：从合成中挑选 50 条**质量好且 query 多样**的，写入 `rag_eval_query` 表。质量标准：query 描述真实需求（不是简单复述章节标题）、覆盖不同行业/层级。
3. **隔离检验集**：另留 10 条**未参与合成训练的"陌生 query"**做 sanity check（防止合成 query 因见过原文而虚高分数）。

### 评估指标

| 指标 | 含义 |
|---|---|
| Recall@10 | Top-10 命中任一 gold_chunk 的 query 比例 |
| MRR | 第一个命中 gold_chunk 的倒数排名平均 |
| NDCG@10 | 命中位置加权（rank 越靠前权重越高） |
| Rerank Lift | NDCG@10(rerank) - NDCG@10(no_rerank)，目标 ≥ 0.10 |

### 评估流水线

`POST /api/rag/eval/run` → 遍历 50 条 query → 跑两次（with/without rerank）→ 把每条 query 的指标与汇总指标写入 `prompt_eval_run` 表（沿用阶段 0 plan §2.1 中预留的表）/ 或新建 `rag_eval_run` 表。报告 JSON 输出。

---

## §7 关键文件结构（新增）

```
src/main/java/com/leo/enterpriseinertraining/
├── rag/
│   ├── ingest/
│   │   ├── PdfIngestPipeline.java       ← 编排：read → split → embed → write
│   │   ├── PdfStructuredReader.java     ← 封装 ParagraphPdfDocumentReader
│   │   └── ContentHashCalculator.java   ← SHA-256 去重
│   ├── store/
│   │   ├── VectorStore.java             ← PGVector R2DBC/JDBC 适配
│   │   └── BM25Store.java               ← ES RestClient 适配
│   ├── search/
│   │   ├── HybridRetriever.java
│   │   ├── RrfFusion.java               ← 静态工具方法
│   │   └── GteReranker.java             ← 阿里云 rerank HTTP 调用
│   ├── eval/
│   │   ├── QuerySynthesizer.java        ← LLM 合成 query
│   │   ├── RagEvaluator.java            ← Recall@k / MRR / NDCG
│   │   └── EvalRunner.java
│   └── controller/RagController.java
├── entity/
│   ├── KnowledgeDoc.java                ← extends BaseEntity
│   ├── KnowledgeChunk.java              ← extends BaseEntity
│   └── RagEvalQuery.java                ← extends BaseEntity
├── mapper/                              ← KnowledgeDocMapper / KnowledgeChunkMapper / RagEvalQueryMapper
├── service/                             ← KnowledgeService / RagSearchService / RagEvalService（含 impl/）
├── dto/                                 ← RagSearchRequest / RagIngestRequest 等
├── vo/                                  ← RagHitVO / CitationVO / EvalSummaryVO
└── config/
    ├── PgVectorDataSourceConfig.java    ← 第二个数据源（PGVector）
    └── ElasticsearchConfig.java         ← RestClient bean

src/main/resources/
├── db/
│   ├── schema-rag.sql                   ← knowledge_doc / chunk / eval_query
│   └── pgvector-init.sql                ← CREATE EXTENSION + table + HNSW
├── es/
│   └── knowledge_chunk_bm25.mapping.json
└── corpus/                              ← .gitignore，单独脚本下载

docker/
└── es/
    ├── Dockerfile                       ← elasticsearch:8.13.0 + IK 8.13.0
    └── ik-plugin-install.sh
```

---

## §8 阶段化交付（7-10 天，14 个 Task 量级）

| 周序 | 子模块 | 关键产物 |
|---|---|---|
| T1-T2 | schema-rag.sql + 3 个 entity + mapper + PGVector init SQL + ES index mapping | DDL/索引一键创建 |
| T3 | ES 镜像装 IK 分词器（docker/es/Dockerfile） | docker-compose ES 服务可用 IK |
| T4 | PdfStructuredReader（封装 ParagraphPdfDocumentReader）+ ContentHashCalculator | 输入 PDF → 输出 List<Document>（带 metadata） |
| T5 | EmbeddingClient（Spring AI OpenAiEmbeddingModel 包装，指向 DashScope）| query 文本 → 1024d 向量 |
| T6 | VectorStore（PGVector 写入 + cosine 查询）| `upsert(chunk)` / `search(vector, topK)` |
| T7 | BM25Store（ES 写入 + match 查询）| `upsert(chunk)` / `search(text, topK)` |
| T8 | PdfIngestPipeline + `POST /api/rag/ingest` | 5 篇 sample PDF 入库可见 |
| T9 | 预置语料下载脚本 + 30-50 篇 PDF ingest 完成 | `knowledge_doc` 表有 30-50 行 |
| T10 | HybridRetriever + RrfFusion + `GET /api/rag/search?useRerank=false` | 双路融合返回 Top-K（无 rerank）|
| T11 | GteReranker + `useRerank=true` 分支 | rerank 后 Top-K |
| T12 | QuerySynthesizer + 合成 100 条 query + 人工筛选到 50 条 + 写入 DB | `rag_eval_query` 表 50 行 |
| T13 | RagEvaluator + `POST /api/rag/eval/run` + `GET /api/rag/eval/latest` | 评估 JSON 报告 |
| T14 | 端到端冒烟（ingest → search → eval）+ 性能验证 + 阶段 1 收尾 commit | 出口条件勾选 |

---

## §9 验证方式

### 功能层
- [ ] `POST /api/rag/ingest` 执行 5 篇样本 PDF → `knowledge_doc`(5), `knowledge_chunk`(≈150), PGVector(≈150), ES(≈150) 行数对齐
- [ ] `GET /api/rag/search?q=...&topK=10&useRerank=true` 返回 10 条带引用的 hits（包含 docTitle/sectionTitle/pageRange）
- [ ] `POST /api/rag/eval/run` 返回 JSON 报告，包含 Recall@10/MRR/NDCG@10 + with/without rerank 对比

### 性能层
- [ ] 单次 search P95 ≤ 1.5s（Hybrid 并发 + Rerank 一次 API 往返）
- [ ] Ingest 50 篇 PDF（约 1500 个 chunk）≤ 5 分钟

### 质量层
- [ ] **Recall@10 ≥ 0.80**
- [ ] **MRR ≥ 0.55**
- [ ] **Rerank Lift（NDCG@10 提升）≥ 0.10**
- [ ] 隔离的"陌生 query"集 Recall@10 不应低于合成集 Recall@10 超过 0.15（合理范围内的泛化）

### 工程层
- [ ] 同一 PDF 重复 ingest（content_hash 一致）会被跳过
- [ ] PGVector / ES / MySQL 三库行数始终一致
- [ ] 出口前 `mvn test` 全绿

---

## §10 后续步骤

1. spec 自检后写入 git
2. invoke `superpowers:writing-plans` 把 §8 的 14 个 Task 展开为可执行实施计划
3. 按 subagent-driven 模式逐 Task 执行 + 两阶段 review
4. 阶段 1 收口后进入阶段 2（Workflow 引擎 + 单 Agent）
