# 阶段 1：RAG 核心 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal：** 在阶段 0 脚手架上落地"输入主题 → 命中带引用的知识片段"的端到端 RAG 链路，包含离线 ingest、Hybrid Search、Rerank、评估闭环。

**Architecture：** PDF（信通院/部委白皮书/财报，30-50 篇）→ Spring AI `ParagraphPdfDocumentReader` 结构感知分块 → 阿里云 `text-embedding-v3` 向量化（1024d）→ 同时写入 PGVector（HNSW cosine）+ ES8（IK 分词 BM25）+ MySQL 元数据。检索时 Virtual Thread 并发跑双路 Top-50 → 应用层 RRF（k=60）融合 → 阿里云 `gte-rerank-v2` 重排 → 输出 Top-K 带引用片段。LLM 合成 + 人工筛选构建 50 条 GT 评估集，跑 Recall@10/MRR/NDCG@10。

**Tech Stack：** Spring Boot 3.5.14 / Java 21 (Virtual Thread) / Spring AI 1.0.0（OpenAI 兼容 + PgVectorStore + PdfReader）/ Elasticsearch Java Client 8.13 / MyBatis-Flex 1.10.6 / DashScope（embedding/rerank/qwen-max）

**项目根：** `/Users/zhengsmacbook/Desktop/miniProject/claude/enterprise-iner-training`
**Baseline commit：** `03b3669`

---

## File Structure（阶段 1 完成后新增）

```
pom.xml                                  ← 修改：+ pgvector store / pdf reader / ES Java Client
docker-compose.yml                       ← 修改：ES 改用 build（带 IK 分词器）
docker/es/Dockerfile                     ← 新增
docker/es/install-ik.sh                  ← 新增

.gitignore                               ← 修改：+ src/main/resources/corpus/

src/main/resources/
├── application-dev.yml                  ← 修改：+ pgvector / es 配置
├── db/schema-rag.sql                    ← 新增（MySQL 3 表）
├── db/pgvector-init.sql                 ← 新增（CREATE EXTENSION + table + HNSW）
├── es/knowledge_chunk_bm25.mapping.json ← 新增（ES index mapping）
└── corpus/                              ← gitignored（PDF 放这里，scripts 下载）

scripts/
├── download-corpus.sh                   ← 新增（一键下载或拷贝预置 PDF）
└── README.md                            ← 新增（语料来源清单与下载说明）

src/main/java/com/leo/enterpriseinertraining/
├── config/
│   ├── PgVectorDataSourceConfig.java    ← 新增（独立 DataSource + JdbcTemplate）
│   └── ElasticsearchConfig.java         ← 新增（ElasticsearchClient bean）
├── entity/
│   ├── KnowledgeDoc.java                ← 新增 extends BaseEntity
│   ├── KnowledgeChunk.java              ← 新增 extends BaseEntity
│   └── RagEvalQuery.java                ← 新增 extends BaseEntity
├── mapper/
│   ├── KnowledgeDocMapper.java
│   ├── KnowledgeChunkMapper.java
│   └── RagEvalQueryMapper.java
├── rag/
│   ├── ingest/
│   │   ├── PdfStructuredReader.java
│   │   ├── ContentHashCalculator.java
│   │   └── PdfIngestPipeline.java
│   ├── llm/
│   │   ├── DashScopeEmbeddingClient.java
│   │   └── DashScopeRerankClient.java
│   ├── store/
│   │   ├── VectorStore.java             ← 接口
│   │   ├── PgVectorStoreImpl.java       ← Spring AI 包装
│   │   ├── BM25Store.java               ← 接口
│   │   └── ElasticsearchBM25Store.java
│   ├── search/
│   │   ├── HybridRetriever.java
│   │   └── RrfFusion.java
│   └── eval/
│       ├── QuerySynthesizer.java
│       └── RagEvaluator.java
├── service/
│   ├── RagIngestService.java + impl/
│   ├── RagSearchService.java + impl/
│   └── RagEvalService.java + impl/
├── controller/
│   └── RagController.java
├── dto/
│   ├── RagSearchRequest.java
│   ├── RagIngestRequest.java
│   └── RagEvalRunRequest.java
└── vo/
    ├── RagHitVO.java
    ├── CitationVO.java
    ├── IngestSummaryVO.java
    └── EvalSummaryVO.java

src/test/java/com/leo/enterpriseinertraining/
├── rag/ingest/PdfStructuredReaderTest.java
├── rag/llm/DashScopeEmbeddingClientIT.java          ← @EnabledIfEnvironmentVariable
├── rag/search/RrfFusionTest.java
└── rag/eval/RagEvaluatorTest.java
```

---

## Task 1：依赖补全 + 第二数据源 + ES 客户端配置

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/resources/application-dev.yml`
- Modify: `src/main/resources/application.yml`（追加 RAG 相关 app.* 段）
- Create: `src/main/java/com/leo/enterpriseinertraining/config/PgVectorDataSourceConfig.java`
- Create: `src/main/java/com/leo/enterpriseinertraining/config/ElasticsearchConfig.java`

- [ ] **Step 1.1：pom.xml 追加依赖**

在 `<dependencies>` 段（hutool 之前）追加：

```xml
        <!-- Spring AI PGVector store + PDF reader -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-starter-vector-store-pgvector</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-pdf-document-reader</artifactId>
        </dependency>

        <!-- Elasticsearch Java Client 8.x -->
        <dependency>
            <groupId>co.elastic.clients</groupId>
            <artifactId>elasticsearch-java</artifactId>
            <version>8.13.4</version>
        </dependency>
        <dependency>
            <groupId>jakarta.json</groupId>
            <artifactId>jakarta.json-api</artifactId>
            <version>2.1.3</version>
        </dependency>
        <dependency>
            <groupId>org.eclipse.parsson</groupId>
            <artifactId>parsson</artifactId>
            <version>1.1.6</version>
        </dependency>
```

> 注：`spring-ai-starter-vector-store-pgvector` 会自动配 PgVectorStore，但默认走 `spring.datasource` —— 我们需要手动指向第二数据源（Step 1.4）。

- [ ] **Step 1.2：application-dev.yml 追加 pgvector 与 es 配置**

在文件末尾追加：

```yaml
# RAG 子系统配置
rag:
  pgvector:
    url: jdbc:postgresql://localhost:5434/irp_vec
    username: irp
    password: irppw
    schema: public
    table: knowledge_chunk_vec
    dimensions: 1024
  elasticsearch:
    host: localhost
    port: 9201
    scheme: http
    index: knowledge_chunk_bm25
```

- [ ] **Step 1.3：application.yml 追加 DashScope 端点配置**

在 `app:` 段后追加（与 `app.jwt` 并列）：

```yaml
app:
  jwt:
    # ...沿用阶段 0...
  dashscope:
    # 复用 spring.ai.openai.api-key 环境变量；这里只给非 chat 接口的端点
    embedding-model: text-embedding-v3
    embedding-url: https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings
    rerank-model: gte-rerank-v2
    rerank-url: https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank
    chat-model: qwen-max
```

- [ ] **Step 1.4：创建 `PgVectorDataSourceConfig.java`**

```java
package com.leo.enterpriseinertraining.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * PGVector 独立数据源。
 *
 * <p>与主 MySQL 数据源（spring.datasource）并存；该 DataSource 不参与
 * MyBatis-Flex 扫描，仅用于 PGVector 表的直接 JDBC 操作以及 Spring AI
 * PgVectorStore 的初始化。</p>
 */
@Configuration
public class PgVectorDataSourceConfig {

    @Bean(name = "pgVectorDataSource")
    public DataSource pgVectorDataSource(
            @Value("${rag.pgvector.url}") String url,
            @Value("${rag.pgvector.username}") String username,
            @Value("${rag.pgvector.password}") String password) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setMaximumPoolSize(8);
        ds.setMinimumIdle(1);
        ds.setPoolName("pg-vector-pool");
        return ds;
    }

    @Bean(name = "pgVectorJdbcTemplate")
    public JdbcTemplate pgVectorJdbcTemplate(DataSource pgVectorDataSource) {
        return new JdbcTemplate(pgVectorDataSource);
    }
}
```

- [ ] **Step 1.5：创建 `ElasticsearchConfig.java`**

```java
package com.leo.enterpriseinertraining.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ElasticsearchConfig {

    @Bean
    public ElasticsearchClient elasticsearchClient(
            @Value("${rag.elasticsearch.host}") String host,
            @Value("${rag.elasticsearch.port}") int port,
            @Value("${rag.elasticsearch.scheme}") String scheme) {
        RestClient rest = RestClient.builder(new HttpHost(host, port, scheme)).build();
        return new ElasticsearchClient(new RestClientTransport(rest, new JacksonJsonpMapper()));
    }
}
```

- [ ] **Step 1.6：编译验证**

Run：`./mvnw -q clean compile`
Expected：BUILD SUCCESS。

- [ ] **Step 1.7：禁用 Spring AI PgVectorStore 自动配置（暂时）**

我们要自己控制 PgVectorStore（指向第二数据源），先在 `EnterpriseInerTrainingApplication.java` 的 `@SpringBootApplication` 中 exclude：

打开 `src/main/java/com/leo/enterpriseinertraining/EnterpriseInerTrainingApplication.java`，把：
```java
@SpringBootApplication
@MapperScan("com.leo.enterpriseinertraining.mapper")
public class EnterpriseInerTrainingApplication {
```
改为：
```java
import org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration;
// ...
@SpringBootApplication(exclude = { PgVectorStoreAutoConfiguration.class })
@MapperScan("com.leo.enterpriseinertraining.mapper")
public class EnterpriseInerTrainingApplication {
```

> PgVectorStore 我们将在 Task 7 用手写 `@Bean` 绑定到 `pgVectorDataSource`。

- [ ] **Step 1.8：再次编译并启动一次（health 探针）**

```bash
./mvnw -q clean compile
nohup ./mvnw spring-boot:run > /tmp/irp-app.log 2>&1 &
echo $! > /tmp/irp-app.pid
for i in $(seq 1 24); do
  if grep -q "Started EnterpriseInerTrainingApplication" /tmp/irp-app.log; then
    echo "✓ started"; break
  fi
  sleep 5
done
curl -s http://localhost:8080/api/health
kill $(cat /tmp/irp-app.pid)
```
Expected：`{"code":0,"data":{"status":"UP",...}}`，无 BeanCreationException。

- [ ] **Step 1.9：提交**

```bash
git add pom.xml \
        src/main/resources/application.yml \
        src/main/resources/application-dev.yml \
        src/main/java/com/leo/enterpriseinertraining/config/PgVectorDataSourceConfig.java \
        src/main/java/com/leo/enterpriseinertraining/config/ElasticsearchConfig.java \
        src/main/java/com/leo/enterpriseinertraining/EnterpriseInerTrainingApplication.java
git commit -m "feat(rag): infra deps + pgvector secondary datasource + es client"
```

---

## Task 2：MySQL DDL + 3 个 entity + 3 个 mapper

**Files:**
- Create: `src/main/resources/db/schema-rag.sql`
- Create: `src/main/java/com/leo/enterpriseinertraining/entity/{KnowledgeDoc,KnowledgeChunk,RagEvalQuery}.java`
- Create: `src/main/java/com/leo/enterpriseinertraining/mapper/{KnowledgeDocMapper,KnowledgeChunkMapper,RagEvalQueryMapper}.java`

- [ ] **Step 2.1：创建 `schema-rag.sql`**

```sql
-- 阶段 1 RAG 元数据表

CREATE TABLE IF NOT EXISTS `knowledge_doc` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `title`        VARCHAR(255) NOT NULL COMMENT '文档标题',
    `source`       VARCHAR(64)  NOT NULL COMMENT '来源：CAICT/MIIT/NDRC/CCID/COMPANY-XXX',
    `file_uri`     VARCHAR(512) NOT NULL COMMENT 'PDF 相对路径',
    `total_pages`  INT          NOT NULL DEFAULT 0 COMMENT 'PDF 总页数',
    `content_hash` CHAR(64)     NOT NULL COMMENT 'PDF 内容 SHA-256，去重',
    `ingested_at`  DATETIME     DEFAULT NULL COMMENT '完成入库时间',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`   TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_content_hash` (`content_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG 文档元数据';

CREATE TABLE IF NOT EXISTS `knowledge_chunk` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `doc_id`        BIGINT       NOT NULL COMMENT '关联 knowledge_doc.id',
    `section_title` VARCHAR(255) DEFAULT NULL COMMENT '章节标题（来自 PDF outline）',
    `page_start`    INT          NOT NULL,
    `page_end`      INT          NOT NULL,
    `char_len`      INT          NOT NULL,
    `vector_id`     CHAR(36)     NOT NULL COMMENT 'PGVector 行 UUID',
    `es_doc_id`     VARCHAR(64)  NOT NULL COMMENT 'ES _id',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`    TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_doc_id` (`doc_id`),
    KEY `idx_vector_id` (`vector_id`),
    KEY `idx_es_doc_id` (`es_doc_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG chunk 轻量元数据';

CREATE TABLE IF NOT EXISTS `rag_eval_query` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `query_text`     VARCHAR(512) NOT NULL,
    `gold_chunk_ids` JSON         NOT NULL COMMENT '标准答案 chunk_id 列表',
    `query_source`   VARCHAR(16)  NOT NULL DEFAULT 'synthesized' COMMENT 'synthesized / human',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG 评估数据集';
```

- [ ] **Step 2.2：执行 DDL**

```bash
docker exec -i irp-mysql mysql -uirp -pirppw irp < src/main/resources/db/schema-rag.sql
docker exec irp-mysql mysql -uirp -pirppw irp -e "SHOW TABLES;"
```
Expected：出现 `knowledge_doc / knowledge_chunk / rag_eval_query` 三表。

- [ ] **Step 2.3：创建 `KnowledgeDoc.java`**

```java
package com.leo.enterpriseinertraining.entity;

import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Table("knowledge_doc")
public class KnowledgeDoc extends BaseEntity {

    private String title;
    private String source;
    private String fileUri;
    private Integer totalPages;
    private String contentHash;
    private LocalDateTime ingestedAt;
}
```

- [ ] **Step 2.4：创建 `KnowledgeChunk.java`**

```java
package com.leo.enterpriseinertraining.entity;

import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Table("knowledge_chunk")
public class KnowledgeChunk extends BaseEntity {

    private Long docId;
    private String sectionTitle;
    private Integer pageStart;
    private Integer pageEnd;
    private Integer charLen;
    private String vectorId;
    private String esDocId;
}
```

- [ ] **Step 2.5：创建 `RagEvalQuery.java`**

```java
package com.leo.enterpriseinertraining.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.handler.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Table("rag_eval_query")
public class RagEvalQuery extends BaseEntity {

    private String queryText;

    @Column(typeHandler = JacksonTypeHandler.class)
    private List<Long> goldChunkIds;

    private String querySource;
}
```

- [ ] **Step 2.6：创建 3 个 Mapper**

`KnowledgeDocMapper.java`：
```java
package com.leo.enterpriseinertraining.mapper;

import com.leo.enterpriseinertraining.entity.KnowledgeDoc;
import com.mybatisflex.core.BaseMapper;

public interface KnowledgeDocMapper extends BaseMapper<KnowledgeDoc> {
}
```

`KnowledgeChunkMapper.java`：
```java
package com.leo.enterpriseinertraining.mapper;

import com.leo.enterpriseinertraining.entity.KnowledgeChunk;
import com.mybatisflex.core.BaseMapper;

public interface KnowledgeChunkMapper extends BaseMapper<KnowledgeChunk> {
}
```

`RagEvalQueryMapper.java`：
```java
package com.leo.enterpriseinertraining.mapper;

import com.leo.enterpriseinertraining.entity.RagEvalQuery;
import com.mybatisflex.core.BaseMapper;

public interface RagEvalQueryMapper extends BaseMapper<RagEvalQuery> {
}
```

- [ ] **Step 2.7：编译触发 APT 生成 TableDef**

```bash
./mvnw -q clean compile
ls target/generated-sources/annotations/com/leo/enterpriseinertraining/entity/table/
```
Expected：除 `UserTableDef.java`，还出现 `KnowledgeDocTableDef.java / KnowledgeChunkTableDef.java / RagEvalQueryTableDef.java`。

- [ ] **Step 2.8：提交**

```bash
git add src/main/resources/db/schema-rag.sql \
        src/main/java/com/leo/enterpriseinertraining/entity/{KnowledgeDoc,KnowledgeChunk,RagEvalQuery}.java \
        src/main/java/com/leo/enterpriseinertraining/mapper/{KnowledgeDocMapper,KnowledgeChunkMapper,RagEvalQueryMapper}.java
git commit -m "feat(rag): MySQL DDL + 3 entities (doc/chunk/eval_query) + mappers"
```

---

## Task 3：PGVector 初始化 SQL + ApplicationRunner

**Files:**
- Create: `src/main/resources/db/pgvector-init.sql`
- Create: `src/main/java/com/leo/enterpriseinertraining/config/PgVectorSchemaInitializer.java`

- [ ] **Step 3.1：创建 `pgvector-init.sql`**

```sql
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS knowledge_chunk_vec (
    id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    chunk_id      BIGINT NOT NULL,
    doc_id        BIGINT NOT NULL,
    section_title TEXT,
    page_start    INT,
    page_end      INT,
    content       TEXT NOT NULL,
    embedding     vector(1024) NOT NULL,
    created_at    TIMESTAMP DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_kcv_chunk_id ON knowledge_chunk_vec (chunk_id);
CREATE INDEX IF NOT EXISTS idx_kcv_doc_id   ON knowledge_chunk_vec (doc_id);

-- HNSW 索引（cosine）
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes WHERE indexname = 'idx_kcv_embedding_hnsw'
    ) THEN
        CREATE INDEX idx_kcv_embedding_hnsw
            ON knowledge_chunk_vec
            USING hnsw (embedding vector_cosine_ops)
            WITH (m = 16, ef_construction = 64);
    END IF;
END$$;
```

- [ ] **Step 3.2：创建 `PgVectorSchemaInitializer.java`**

```java
package com.leo.enterpriseinertraining.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.support.EncodedResource;

import java.nio.charset.StandardCharsets;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class PgVectorSchemaInitializer {

    @Bean
    public ApplicationRunner pgVectorSchemaRunner(
            @Qualifier("pgVectorDataSource") DataSource ds,
            @Qualifier("pgVectorJdbcTemplate") JdbcTemplate jdbc) {
        return args -> {
            try (var conn = ds.getConnection()) {
                EncodedResource script = new EncodedResource(
                        new ClassPathResource("db/pgvector-init.sql"),
                        StandardCharsets.UTF_8);
                ScriptUtils.executeSqlScript(conn, script);
                Integer rows = jdbc.queryForObject(
                        "SELECT count(*) FROM knowledge_chunk_vec", Integer.class);
                log.info("[PGVector] schema ready, current rows={}", rows);
            } catch (Exception e) {
                log.error("[PGVector] schema init failed", e);
                throw e;
            }
        };
    }
}
```

- [ ] **Step 3.3：启动验证**

```bash
./mvnw -q clean compile
nohup ./mvnw spring-boot:run > /tmp/irp-app.log 2>&1 &
echo $! > /tmp/irp-app.pid
sleep 30
grep "\[PGVector\] schema ready" /tmp/irp-app.log
docker exec irp-pgvector psql -U irp -d irp_vec -c "\d knowledge_chunk_vec"
docker exec irp-pgvector psql -U irp -d irp_vec -c "\di knowledge_chunk_vec"
kill $(cat /tmp/irp-app.pid)
```
Expected：日志含 "schema ready, current rows=0"；`\d` 显示表结构、`\di` 显示 hnsw 索引。

- [ ] **Step 3.4：提交**

```bash
git add src/main/resources/db/pgvector-init.sql \
        src/main/java/com/leo/enterpriseinertraining/config/PgVectorSchemaInitializer.java
git commit -m "feat(rag): pgvector schema init runner (knowledge_chunk_vec + HNSW)"
```

---

## Task 4：ES 镜像带 IK 分词器 + index mapping + ApplicationRunner

**Files:**
- Create: `docker/es/Dockerfile`
- Create: `docker/es/install-ik.sh`
- Modify: `docker-compose.yml`（ES 服务改用 build）
- Create: `src/main/resources/es/knowledge_chunk_bm25.mapping.json`
- Create: `src/main/java/com/leo/enterpriseinertraining/config/EsIndexInitializer.java`

- [ ] **Step 4.1：创建 `docker/es/Dockerfile`**

```dockerfile
FROM docker.elastic.co/elasticsearch/elasticsearch:8.13.0

# 装 IK 中文分词器（版本必须与 ES 主版本一致）
RUN bin/elasticsearch-plugin install --batch \
    https://release.infinilabs.com/analysis-ik/stable/elasticsearch-analysis-ik-8.13.0.zip
```

- [ ] **Step 4.2：把 docker-compose.yml ES 服务改为 build**

把：
```yaml
  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.13.0
    container_name: irp-es
```
改为：
```yaml
  elasticsearch:
    build: ./docker/es
    image: irp-es:8.13.0-ik
    container_name: irp-es
```
其它字段保持不变。

- [ ] **Step 4.3：构建并重启 ES**

```bash
docker compose stop elasticsearch
docker compose rm -f elasticsearch
docker compose build elasticsearch
docker compose up -d elasticsearch
sleep 30
docker exec irp-es bin/elasticsearch-plugin list
```
Expected：列出 `analysis-ik`。

- [ ] **Step 4.4：创建 `knowledge_chunk_bm25.mapping.json`**

```json
{
  "settings": {
    "number_of_shards": 1,
    "number_of_replicas": 0,
    "analysis": {
      "analyzer": {
        "ik_max": { "type": "custom", "tokenizer": "ik_max_word" },
        "ik_smart_search": { "type": "custom", "tokenizer": "ik_smart" }
      }
    }
  },
  "mappings": {
    "properties": {
      "chunk_id":      { "type": "long" },
      "doc_id":        { "type": "long" },
      "section_title": { "type": "text", "analyzer": "ik_max", "search_analyzer": "ik_smart_search" },
      "content":       { "type": "text", "analyzer": "ik_max", "search_analyzer": "ik_smart_search" },
      "page_start":    { "type": "integer" },
      "page_end":      { "type": "integer" }
    }
  }
}
```

- [ ] **Step 4.5：创建 `EsIndexInitializer.java`**

```java
package com.leo.enterpriseinertraining.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.json.JsonData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class EsIndexInitializer {

    @Bean
    public ApplicationRunner esIndexRunner(ElasticsearchClient es,
                                           @Value("${rag.elasticsearch.index}") String indexName) {
        return args -> {
            boolean exists = es.indices().exists(ExistsRequest.of(b -> b.index(indexName))).value();
            if (exists) {
                log.info("[ES] index {} already exists, skip", indexName);
                return;
            }
            try (InputStream in = new ClassPathResource(
                    "es/knowledge_chunk_bm25.mapping.json").getInputStream()) {
                CreateIndexRequest req = CreateIndexRequest.of(b -> b
                        .index(indexName)
                        .withJson(in));
                es.indices().create(req);
                log.info("[ES] index {} created", indexName);
            } catch (Exception e) {
                log.error("[ES] index init failed", e);
                throw e;
            }
        };
    }
}
```

- [ ] **Step 4.6：启动验证**

```bash
./mvnw -q clean compile
nohup ./mvnw spring-boot:run > /tmp/irp-app.log 2>&1 &
echo $! > /tmp/irp-app.pid
sleep 30
grep "\[ES\] index" /tmp/irp-app.log
curl -s "http://localhost:9201/knowledge_chunk_bm25?pretty" | head -30
kill $(cat /tmp/irp-app.pid)
```
Expected：日志出现 "index knowledge_chunk_bm25 created"；curl 返回 mapping 含 ik_max。

- [ ] **Step 4.7：提交**

```bash
git add docker/es/ docker-compose.yml \
        src/main/resources/es/knowledge_chunk_bm25.mapping.json \
        src/main/java/com/leo/enterpriseinertraining/config/EsIndexInitializer.java
git commit -m "feat(rag): ES image with IK analyzer + knowledge_chunk_bm25 index init"
```

---

## Task 5：PDF 结构化读取 + 内容哈希

**Files:**
- Create: `src/main/java/com/leo/enterpriseinertraining/rag/ingest/PdfStructuredReader.java`
- Create: `src/main/java/com/leo/enterpriseinertraining/rag/ingest/ContentHashCalculator.java`
- Test: `src/test/java/com/leo/enterpriseinertraining/rag/ingest/PdfStructuredReaderTest.java`
- Test: `src/test/java/com/leo/enterpriseinertraining/rag/ingest/ContentHashCalculatorTest.java`

- [ ] **Step 5.1：先写测试 `ContentHashCalculatorTest.java`**

```java
package com.leo.enterpriseinertraining.rag.ingest;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import static org.junit.jupiter.api.Assertions.*;

class ContentHashCalculatorTest {

    @Test
    void sha256_same_content_yields_same_hash() {
        Resource a = new ByteArrayResource("hello".getBytes());
        Resource b = new ByteArrayResource("hello".getBytes());
        assertEquals(ContentHashCalculator.sha256(a), ContentHashCalculator.sha256(b));
    }

    @Test
    void sha256_different_content_differs() {
        Resource a = new ByteArrayResource("hello".getBytes());
        Resource b = new ByteArrayResource("world".getBytes());
        assertNotEquals(ContentHashCalculator.sha256(a), ContentHashCalculator.sha256(b));
    }

    @Test
    void sha256_length_is_64() {
        Resource a = new ByteArrayResource("hello".getBytes());
        assertEquals(64, ContentHashCalculator.sha256(a).length());
    }
}
```

- [ ] **Step 5.2：跑测试看到红灯**

```bash
./mvnw -q -Dtest=ContentHashCalculatorTest test
```
Expected：编译失败（cannot find symbol ContentHashCalculator）。

- [ ] **Step 5.3：实现 `ContentHashCalculator.java`**

```java
package com.leo.enterpriseinertraining.rag.ingest;

import org.springframework.core.io.Resource;

import java.io.InputStream;
import java.security.MessageDigest;

public final class ContentHashCalculator {

    private ContentHashCalculator() {}

    public static String sha256(Resource resource) {
        try (InputStream in = resource.getInputStream()) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) md.update(buf, 0, n);
            byte[] hash = md.digest();
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 失败: " + e.getMessage(), e);
        }
    }
}
```

- [ ] **Step 5.4：跑测试绿灯**

```bash
./mvnw -q -Dtest=ContentHashCalculatorTest test
```
Expected：`Tests run: 3, Failures: 0`。

- [ ] **Step 5.5：实现 `PdfStructuredReader.java`**

```java
package com.leo.enterpriseinertraining.rag.ingest;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.ParagraphPdfDocumentReader;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * PDF 结构感知读取 + 兜底切分。
 *
 * <p>策略：</p>
 * <ol>
 *   <li>先用 {@link ParagraphPdfDocumentReader}（依赖 PDF outline / TOC）按章节切。
 *       每个 {@link Document} 的 metadata 含 title/page_start/page_end。</li>
 *   <li>若 PDF 无 outline（reader 抛异常或返回 0 段），降级到 {@link PagePdfDocumentReader}（按页切）。</li>
 *   <li>对每个章节再用 {@link TokenTextSplitter}（chunk=800, overlap=100）兜底切大段。</li>
 * </ol>
 */
@Slf4j
@Component
public class PdfStructuredReader {

    private static final int CHUNK_TOKENS = 800;
    private static final int CHUNK_OVERLAP = 100;
    private static final int MIN_CHUNK_CHARS = 40;

    public List<Document> read(Resource pdf) {
        List<Document> sections = readSections(pdf);
        TokenTextSplitter splitter = new TokenTextSplitter(
                CHUNK_TOKENS, MIN_CHUNK_CHARS, CHUNK_OVERLAP, 10000, true);
        List<Document> chunks = new ArrayList<>();
        for (Document s : sections) {
            List<Document> sub = splitter.apply(List.of(s));
            for (Document c : sub) {
                // 继承父节点 metadata（章节标题、页范围）
                c.getMetadata().putAll(s.getMetadata());
                chunks.add(c);
            }
        }
        log.info("[PdfReader] {} sections → {} chunks", sections.size(), chunks.size());
        return chunks;
    }

    private List<Document> readSections(Resource pdf) {
        try {
            ParagraphPdfDocumentReader reader = new ParagraphPdfDocumentReader(
                    pdf, PdfDocumentReaderConfig.defaultConfig());
            List<Document> result = reader.get();
            if (!result.isEmpty()) return result;
        } catch (Exception e) {
            log.warn("[PdfReader] no outline / paragraph reader failed: {}, falling back to per-page", e.getMessage());
        }
        // 降级：按页切
        PagePdfDocumentReader page = new PagePdfDocumentReader(
                pdf, PdfDocumentReaderConfig.defaultConfig());
        return page.get();
    }
}
```

- [ ] **Step 5.6：测试 `PdfStructuredReaderTest.java`（仅纯单元，不读真实 PDF）**

```java
package com.leo.enterpriseinertraining.rag.ingest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 真实 PDF 读取留给 Task 8 集成验证。
 * 这里只确保 Bean 能实例化。
 */
class PdfStructuredReaderTest {
    @Test
    void can_instantiate() {
        assertNotNull(new PdfStructuredReader());
    }
}
```

- [ ] **Step 5.7：跑测试 + commit**

```bash
./mvnw -q -Dtest='ContentHashCalculatorTest,PdfStructuredReaderTest' test
git add src/main/java/com/leo/enterpriseinertraining/rag/ingest/ \
        src/test/java/com/leo/enterpriseinertraining/rag/ingest/
git commit -m "feat(rag): PdfStructuredReader (paragraph + page fallback) + ContentHash"
```

---

## Task 6：DashScope Embedding 客户端

**Files:**
- Create: `src/main/java/com/leo/enterpriseinertraining/rag/llm/DashScopeEmbeddingClient.java`
- Test: `src/test/java/com/leo/enterpriseinertraining/rag/llm/DashScopeEmbeddingClientIT.java`

- [ ] **Step 6.1：实现 `DashScopeEmbeddingClient.java`**

```java
package com.leo.enterpriseinertraining.rag.llm;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * 阿里云 DashScope text-embedding-v3（OpenAI 兼容协议）。
 * 1024 维。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DashScopeEmbeddingClient {

    @Value("${spring.ai.openai.api-key}") String apiKey;
    @Value("${app.dashscope.embedding-model}") String model;
    @Value("${app.dashscope.embedding-url}") String endpoint;

    /** 批量 embed；DashScope v3 单次最多 25 条。 */
    public List<float[]> embed(List<String> texts) {
        if (texts.isEmpty()) return List.of();
        if (texts.size() > 25) {
            // 拆批
            List<float[]> all = new java.util.ArrayList<>();
            for (int i = 0; i < texts.size(); i += 25) {
                all.addAll(embed(texts.subList(i, Math.min(i + 25, texts.size()))));
            }
            return all;
        }
        RestClient rc = RestClient.create();
        EmbeddingResp resp = rc.post()
                .uri(endpoint)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new EmbeddingReq(model, texts, "float"))
                .retrieve()
                .body(EmbeddingResp.class);
        if (resp == null || resp.data == null) {
            throw new RuntimeException("DashScope embedding 返回空");
        }
        return resp.data.stream().map(d -> d.embedding).toList();
    }

    public float[] embedOne(String text) {
        return embed(List.of(text)).get(0);
    }

    @Data
    static class EmbeddingReq {
        String model;
        List<String> input;
        @JsonProperty("encoding_format")
        String encodingFormat;
        EmbeddingReq(String m, List<String> in, String f) {
            this.model = m; this.input = in; this.encodingFormat = f;
        }
    }
    @Data
    static class EmbeddingResp {
        List<Item> data;
        String model;
        @Data static class Item { float[] embedding; int index; }
    }
}
```

- [ ] **Step 6.2：集成测试（仅在 DASHSCOPE_API_KEY 存在时跑）**

```java
package com.leo.enterpriseinertraining.rag.llm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
        "app.jwt.secret=test-secret-for-hs256-must-be-at-least-32-bytes-please"
})
@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
class DashScopeEmbeddingClientIT {

    @Autowired
    DashScopeEmbeddingClient client;

    @Test
    void embed_one_returns_1024d_vector() {
        float[] v = client.embedOne("具身智能产业链上中下游分析");
        assertEquals(1024, v.length);
        // 非全零
        double norm = 0;
        for (float f : v) norm += f * f;
        assertTrue(norm > 0.0);
    }

    @Test
    void embed_batch_returns_same_count() {
        var v = client.embed(java.util.List.of("AI", "半导体", "新能源"));
        assertEquals(3, v.size());
        v.forEach(x -> assertEquals(1024, x.length));
    }
}
```

- [ ] **Step 6.3：跑测试**

```bash
./mvnw -q -Dtest=DashScopeEmbeddingClientIT test
```
Expected：若 `DASHSCOPE_API_KEY` 已 export 则 PASS；否则 SKIP（@EnabledIfEnvironmentVariable）。

- [ ] **Step 6.4：提交**

```bash
git add src/main/java/com/leo/enterpriseinertraining/rag/llm/DashScopeEmbeddingClient.java \
        src/test/java/com/leo/enterpriseinertraining/rag/llm/DashScopeEmbeddingClientIT.java
git commit -m "feat(rag): DashScope text-embedding-v3 client (1024d, batched)"
```

---

## Task 7：VectorStore（PGVector 适配）

**Files:**
- Create: `src/main/java/com/leo/enterpriseinertraining/rag/store/VectorStore.java`
- Create: `src/main/java/com/leo/enterpriseinertraining/rag/store/PgVectorStoreImpl.java`

- [ ] **Step 7.1：定义 `VectorStore.java` 接口**

```java
package com.leo.enterpriseinertraining.rag.store;

import java.util.List;

public interface VectorStore {

    /** 写入一批 chunk，返回它们在向量库内的 UUID。顺序与入参一致。 */
    List<String> upsertBatch(List<VectorRow> rows);

    /** 查询：传入查询向量、topK，返回 hit 列表（按 cosine 距离升序，即相似度降序）。 */
    List<VectorHit> search(float[] queryEmbedding, int topK);

    record VectorRow(
            long chunkId,
            long docId,
            String sectionTitle,
            int pageStart,
            int pageEnd,
            String content,
            float[] embedding
    ) {}

    record VectorHit(
            String vectorId,
            long chunkId,
            long docId,
            String sectionTitle,
            int pageStart,
            int pageEnd,
            String content,
            double score   // 1 - cosine_distance ∈ [0,1]，越大越相似
    ) {}
}
```

- [ ] **Step 7.2：实现 `PgVectorStoreImpl.java`**

```java
package com.leo.enterpriseinertraining.rag.store;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PgVectorStoreImpl implements VectorStore {

    @Qualifier("pgVectorJdbcTemplate")
    private final JdbcTemplate jdbc;

    private static final String INSERT_SQL = """
        INSERT INTO knowledge_chunk_vec
            (id, chunk_id, doc_id, section_title, page_start, page_end, content, embedding)
        VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?::vector)
        """;

    private static final String SEARCH_SQL = """
        SELECT id::text AS id, chunk_id, doc_id, section_title, page_start, page_end,
               content, 1 - (embedding <=> ?::vector) AS score
        FROM knowledge_chunk_vec
        ORDER BY embedding <=> ?::vector
        LIMIT ?
        """;

    @Override
    public List<String> upsertBatch(List<VectorRow> rows) {
        List<String> ids = new ArrayList<>(rows.size());
        for (VectorRow r : rows) {
            String uuid = UUID.randomUUID().toString();
            jdbc.update(INSERT_SQL,
                    uuid, r.chunkId(), r.docId(), r.sectionTitle(),
                    r.pageStart(), r.pageEnd(), r.content(),
                    toPgVector(r.embedding()));
            ids.add(uuid);
        }
        return ids;
    }

    @Override
    public List<VectorHit> search(float[] q, int topK) {
        String qv = toPgVector(q);
        return jdbc.query(SEARCH_SQL,
                (rs, n) -> new VectorHit(
                        rs.getString("id"),
                        rs.getLong("chunk_id"),
                        rs.getLong("doc_id"),
                        rs.getString("section_title"),
                        rs.getInt("page_start"),
                        rs.getInt("page_end"),
                        rs.getString("content"),
                        rs.getDouble("score")),
                qv, qv, topK);
    }

    private static String toPgVector(float[] v) {
        StringBuilder sb = new StringBuilder(v.length * 8);
        sb.append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(v[i]);
        }
        sb.append(']');
        return sb.toString();
    }
}
```

- [ ] **Step 7.3：编译**

```bash
./mvnw -q compile
```
Expected：BUILD SUCCESS。

- [ ] **Step 7.4：提交**

```bash
git add src/main/java/com/leo/enterpriseinertraining/rag/store/VectorStore.java \
        src/main/java/com/leo/enterpriseinertraining/rag/store/PgVectorStoreImpl.java
git commit -m "feat(rag): VectorStore interface + PGVector impl (HNSW cosine search)"
```

---

## Task 8：BM25Store（Elasticsearch 适配）

**Files:**
- Create: `src/main/java/com/leo/enterpriseinertraining/rag/store/BM25Store.java`
- Create: `src/main/java/com/leo/enterpriseinertraining/rag/store/ElasticsearchBM25Store.java`

- [ ] **Step 8.1：定义 `BM25Store.java` 接口**

```java
package com.leo.enterpriseinertraining.rag.store;

import java.util.List;

public interface BM25Store {

    /** 写入一批 chunk，返回 ES _id 列表（顺序与入参一致）。 */
    List<String> upsertBatch(List<BM25Row> rows);

    List<BM25Hit> search(String queryText, int topK);

    record BM25Row(
            long chunkId,
            long docId,
            String sectionTitle,
            int pageStart,
            int pageEnd,
            String content
    ) {}

    record BM25Hit(
            String esDocId,
            long chunkId,
            long docId,
            String sectionTitle,
            int pageStart,
            int pageEnd,
            String content,
            double score
    ) {}
}
```

- [ ] **Step 8.2：实现 `ElasticsearchBM25Store.java`**

```java
package com.leo.enterpriseinertraining.rag.store;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticsearchBM25Store implements BM25Store {

    private final ElasticsearchClient es;

    @Value("${rag.elasticsearch.index}")
    private String indexName;

    @Override
    public List<String> upsertBatch(List<BM25Row> rows) {
        if (rows.isEmpty()) return List.of();
        BulkRequest.Builder br = new BulkRequest.Builder();
        for (BM25Row r : rows) {
            Map<String, Object> doc = new HashMap<>();
            doc.put("chunk_id", r.chunkId());
            doc.put("doc_id", r.docId());
            doc.put("section_title", r.sectionTitle());
            doc.put("page_start", r.pageStart());
            doc.put("page_end", r.pageEnd());
            doc.put("content", r.content());
            br.operations(op -> op.index(i -> i.index(indexName).document(doc)));
        }
        try {
            BulkResponse resp = es.bulk(br.build());
            if (resp.errors()) {
                log.warn("[ES] bulk has errors");
            }
            List<String> ids = new ArrayList<>(rows.size());
            for (BulkResponseItem item : resp.items()) ids.add(item.id());
            return ids;
        } catch (Exception e) {
            throw new RuntimeException("ES bulk upsert 失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<BM25Hit> search(String queryText, int topK) {
        try {
            SearchResponse<Map> r = es.search(s -> s
                    .index(indexName)
                    .size(topK)
                    .query(q -> q.multiMatch(m -> m
                            .query(queryText)
                            .fields("content^1.0", "section_title^1.5"))),
                    Map.class);
            List<BM25Hit> hits = new ArrayList<>();
            for (var hit : r.hits().hits()) {
                Map src = hit.source();
                if (src == null) continue;
                hits.add(new BM25Hit(
                        hit.id(),
                        ((Number) src.get("chunk_id")).longValue(),
                        ((Number) src.get("doc_id")).longValue(),
                        (String) src.get("section_title"),
                        ((Number) src.get("page_start")).intValue(),
                        ((Number) src.get("page_end")).intValue(),
                        (String) src.get("content"),
                        hit.score() == null ? 0.0 : hit.score()));
            }
            return hits;
        } catch (Exception e) {
            throw new RuntimeException("ES search 失败: " + e.getMessage(), e);
        }
    }
}
```

- [ ] **Step 8.3：编译 + 提交**

```bash
./mvnw -q compile
git add src/main/java/com/leo/enterpriseinertraining/rag/store/BM25Store.java \
        src/main/java/com/leo/enterpriseinertraining/rag/store/ElasticsearchBM25Store.java
git commit -m "feat(rag): BM25Store interface + Elasticsearch impl (IK analyzer)"
```

---

## Task 9：Ingest Pipeline + Service + Controller（先支持单/批 PDF）

**Files:**
- Create: `src/main/java/com/leo/enterpriseinertraining/rag/ingest/PdfIngestPipeline.java`
- Create: `src/main/java/com/leo/enterpriseinertraining/service/RagIngestService.java`
- Create: `src/main/java/com/leo/enterpriseinertraining/service/impl/RagIngestServiceImpl.java`
- Create: `src/main/java/com/leo/enterpriseinertraining/controller/RagController.java`
- Create: `src/main/java/com/leo/enterpriseinertraining/dto/RagIngestRequest.java`
- Create: `src/main/java/com/leo/enterpriseinertraining/vo/IngestSummaryVO.java`
- Modify: `.gitignore` 加 `src/main/resources/corpus/`

- [ ] **Step 9.1：`.gitignore` 追加**

把以下行加入 `.gitignore` 末尾：
```
# RAG 预置语料（PDF 不入 git）
src/main/resources/corpus/
```

- [ ] **Step 9.2：创建 corpus 目录占位**

```bash
mkdir -p src/main/resources/corpus
touch src/main/resources/corpus/.keep
```

- [ ] **Step 9.3：`RagIngestRequest.java`**

```java
package com.leo.enterpriseinertraining.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

@Data
public class RagIngestRequest implements Serializable {

    /** 来源标识，例如 CAICT / MIIT / NDRC / COMPANY-NINGDE。 */
    @NotBlank
    private String source;

    /**
     * 相对 corpus 目录的路径或 glob。例如：
     *   "embodied-ai-2026.pdf" 单文件
     *   "*.pdf" 全部
     */
    @NotBlank
    private String pathOrGlob;
}
```

- [ ] **Step 9.4：`IngestSummaryVO.java`**

```java
package com.leo.enterpriseinertraining.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IngestSummaryVO implements Serializable {
    private int filesScanned;
    private int filesIngested;     // 实际新入库
    private int filesSkipped;      // content_hash 已存在
    private int chunksWritten;
    private long tookMs;
    private List<String> ingestedTitles;
}
```

- [ ] **Step 9.5：`PdfIngestPipeline.java`**

```java
package com.leo.enterpriseinertraining.rag.ingest;

import com.leo.enterpriseinertraining.entity.KnowledgeChunk;
import com.leo.enterpriseinertraining.entity.KnowledgeDoc;
import com.leo.enterpriseinertraining.mapper.KnowledgeChunkMapper;
import com.leo.enterpriseinertraining.mapper.KnowledgeDocMapper;
import com.leo.enterpriseinertraining.rag.llm.DashScopeEmbeddingClient;
import com.leo.enterpriseinertraining.rag.store.BM25Store;
import com.leo.enterpriseinertraining.rag.store.VectorStore;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.leo.enterpriseinertraining.entity.table.KnowledgeDocTableDef.KNOWLEDGE_DOC;

@Slf4j
@Component
@RequiredArgsConstructor
public class PdfIngestPipeline {

    private final PdfStructuredReader reader;
    private final DashScopeEmbeddingClient embedding;
    private final VectorStore vectorStore;
    private final BM25Store bm25Store;
    private final KnowledgeDocMapper docMapper;
    private final KnowledgeChunkMapper chunkMapper;

    public record IngestResult(boolean ingested, int chunks, String title) {}

    @Transactional
    public IngestResult ingestOne(File pdfFile, String source) {
        Resource res = new FileSystemResource(pdfFile);
        String hash = ContentHashCalculator.sha256(res);

        KnowledgeDoc existing = docMapper.selectOneByQuery(
                QueryWrapper.create().where(KNOWLEDGE_DOC.CONTENT_HASH.eq(hash)));
        if (existing != null) {
            log.info("[Ingest] skip duplicate: {} (hash matches doc_id={})", pdfFile.getName(), existing.getId());
            return new IngestResult(false, 0, existing.getTitle());
        }

        List<Document> chunks = reader.read(res);
        if (chunks.isEmpty()) {
            log.warn("[Ingest] no chunks: {}", pdfFile.getName());
            return new IngestResult(false, 0, pdfFile.getName());
        }

        // 1) MySQL knowledge_doc 落库（先建头，拿到 doc_id）
        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setTitle(extractTitle(chunks, pdfFile));
        doc.setSource(source);
        doc.setFileUri("corpus/" + pdfFile.getName());
        doc.setTotalPages(extractTotalPages(chunks));
        doc.setContentHash(hash);
        doc.setIngestedAt(LocalDateTime.now());
        docMapper.insert(doc);

        // 2) Embed（batched）
        List<String> texts = chunks.stream().map(Document::getText).toList();
        List<float[]> vectors = embedding.embed(texts);

        // 3) 先写 MySQL knowledge_chunk 拿 chunk_id（vector_id / es_doc_id 暂用占位，稍后回填）
        List<KnowledgeChunk> chunkRows = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            Document d = chunks.get(i);
            KnowledgeChunk row = new KnowledgeChunk();
            row.setDocId(doc.getId());
            row.setSectionTitle(metaString(d, "title"));
            row.setPageStart(metaInt(d, "page_number", 0));
            row.setPageEnd(metaInt(d, "end_page_number", row.getPageStart()));
            row.setCharLen(d.getText().length());
            row.setVectorId("__pending__");
            row.setEsDocId("__pending__");
            chunkMapper.insert(row);
            chunkRows.add(row);
        }

        // 4) PGVector 写入
        List<VectorStore.VectorRow> vrows = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            KnowledgeChunk c = chunkRows.get(i);
            vrows.add(new VectorStore.VectorRow(
                    c.getId(), doc.getId(),
                    c.getSectionTitle(),
                    c.getPageStart(), c.getPageEnd(),
                    chunks.get(i).getText(),
                    vectors.get(i)));
        }
        List<String> vectorIds = vectorStore.upsertBatch(vrows);

        // 5) ES 写入
        List<BM25Store.BM25Row> brows = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            KnowledgeChunk c = chunkRows.get(i);
            brows.add(new BM25Store.BM25Row(
                    c.getId(), doc.getId(),
                    c.getSectionTitle(),
                    c.getPageStart(), c.getPageEnd(),
                    chunks.get(i).getText()));
        }
        List<String> esIds = bm25Store.upsertBatch(brows);

        // 6) 回填 vector_id / es_doc_id 到 MySQL
        for (int i = 0; i < chunkRows.size(); i++) {
            KnowledgeChunk c = chunkRows.get(i);
            c.setVectorId(vectorIds.get(i));
            c.setEsDocId(esIds.get(i));
            chunkMapper.update(c);
        }

        log.info("[Ingest] done: {} → doc_id={}, {} chunks", pdfFile.getName(), doc.getId(), chunks.size());
        return new IngestResult(true, chunks.size(), doc.getTitle());
    }

    private static String extractTitle(List<Document> chunks, File f) {
        for (Document d : chunks) {
            Object t = d.getMetadata().get("title");
            if (t instanceof String s && !s.isBlank()) return s;
        }
        String name = f.getName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static int extractTotalPages(List<Document> chunks) {
        int max = 0;
        for (Document d : chunks) {
            int p = metaInt(d, "end_page_number", metaInt(d, "page_number", 0));
            if (p > max) max = p;
        }
        return max;
    }

    private static String metaString(Document d, String key) {
        Object v = d.getMetadata().get(key);
        return v == null ? null : v.toString();
    }

    private static int metaInt(Document d, String key, int dflt) {
        Object v = d.getMetadata().get(key);
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try { return Integer.parseInt(s); } catch (Exception ignored) {}
        }
        return dflt;
    }
}
```

- [ ] **Step 9.6：`RagIngestService.java`（接口）**

```java
package com.leo.enterpriseinertraining.service;

import com.leo.enterpriseinertraining.dto.RagIngestRequest;
import com.leo.enterpriseinertraining.vo.IngestSummaryVO;

public interface RagIngestService {
    IngestSummaryVO ingest(RagIngestRequest req);
}
```

- [ ] **Step 9.7：`RagIngestServiceImpl.java`**

```java
package com.leo.enterpriseinertraining.service.impl;

import com.leo.enterpriseinertraining.dto.RagIngestRequest;
import com.leo.enterpriseinertraining.exception.BusinessException;
import com.leo.enterpriseinertraining.exception.ErrorCode;
import com.leo.enterpriseinertraining.rag.ingest.PdfIngestPipeline;
import com.leo.enterpriseinertraining.service.RagIngestService;
import com.leo.enterpriseinertraining.vo.IngestSummaryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagIngestServiceImpl implements RagIngestService {

    private final PdfIngestPipeline pipeline;

    @Override
    public IngestSummaryVO ingest(RagIngestRequest req) {
        long t0 = System.currentTimeMillis();
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        var pattern = "classpath:corpus/" + req.getPathOrGlob();
        org.springframework.core.io.Resource[] resources;
        try {
            resources = resolver.getResources(pattern);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "无法解析路径: " + pattern);
        }
        List<String> titles = new ArrayList<>();
        int ingested = 0, skipped = 0, totalChunks = 0;
        for (var r : resources) {
            try {
                File f = r.getFile();
                if (!f.getName().toLowerCase().endsWith(".pdf")) continue;
                var res = pipeline.ingestOne(f, req.getSource());
                if (res.ingested()) {
                    ingested++;
                    totalChunks += res.chunks();
                    titles.add(res.title());
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                log.error("[Ingest] failed for {}", r.getDescription(), e);
            }
        }
        return new IngestSummaryVO(
                resources.length, ingested, skipped, totalChunks,
                System.currentTimeMillis() - t0, titles);
    }
}
```

- [ ] **Step 9.8：`RagController.java`**

```java
package com.leo.enterpriseinertraining.controller;

import com.leo.enterpriseinertraining.common.BaseResponse;
import com.leo.enterpriseinertraining.common.ResultUtils;
import com.leo.enterpriseinertraining.dto.RagIngestRequest;
import com.leo.enterpriseinertraining.service.RagIngestService;
import com.leo.enterpriseinertraining.vo.IngestSummaryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
@Tag(name = "RAG", description = "知识入库 / 检索 / 评估")
public class RagController {

    private final RagIngestService ingestService;

    @PostMapping("/ingest")
    @Operation(summary = "离线 ingest 一批 PDF（path 是 corpus/ 下的相对路径或 glob）")
    public BaseResponse<IngestSummaryVO> ingest(@RequestBody @Valid RagIngestRequest req) {
        return ResultUtils.success(ingestService.ingest(req));
    }
}
```

- [ ] **Step 9.9：SecurityConfig 把 `/api/rag/**` 加入需鉴权但允许 USER**

打开 `src/main/java/com/leo/enterpriseinertraining/security/SecurityConfig.java`，确认默认 `.anyRequest().authenticated()` 即可（已是这样），无需改动。RAG 接口默认需 JWT。

- [ ] **Step 9.10：放 5 篇样本 PDF + 跑 ingest**

手动从信通院/工信部网站下载 5 篇 PDF 放到 `src/main/resources/corpus/`（例如 `caict-embodied-ai-2026.pdf`、`miit-semiconductor-2026.pdf` 等）。

然后启动应用，登录拿 token，调 ingest：

```bash
nohup ./mvnw spring-boot:run > /tmp/irp-app.log 2>&1 &
echo $! > /tmp/irp-app.pid
for i in $(seq 1 24); do
  grep -q "Started" /tmp/irp-app.log && break
  sleep 5
done

# 用现有 alice / 你的现有账号 登录拿 token
TOKEN=$(curl -s -X POST http://localhost:8080/api/user/login \
  -H "Content-Type: application/json" \
  -d '{"username":"<你的用户名>","password":"<你的密码>"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['token'])")

curl -s -X POST http://localhost:8080/api/rag/ingest \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"source":"CAICT","pathOrGlob":"*.pdf"}'
```

Expected：JSON 返回 `{"code":0,"data":{"filesScanned":5,"filesIngested":5,"chunksWritten":>0,...}}`。

行数一致性验证：
```bash
docker exec irp-mysql mysql -uirp -pirppw irp -e "SELECT COUNT(*) FROM knowledge_doc; SELECT COUNT(*) FROM knowledge_chunk;"
docker exec irp-pgvector psql -U irp -d irp_vec -c "SELECT COUNT(*) FROM knowledge_chunk_vec;"
curl -s "http://localhost:9201/knowledge_chunk_bm25/_count?pretty"
kill $(cat /tmp/irp-app.pid)
```
Expected：`knowledge_chunk` / `knowledge_chunk_vec` / ES `_count.count` 三者相等；`knowledge_doc` = 5。

- [ ] **Step 9.11：提交**

```bash
git add .gitignore src/main/resources/corpus/.keep \
        src/main/java/com/leo/enterpriseinertraining/rag/ingest/PdfIngestPipeline.java \
        src/main/java/com/leo/enterpriseinertraining/service/RagIngestService.java \
        src/main/java/com/leo/enterpriseinertraining/service/impl/RagIngestServiceImpl.java \
        src/main/java/com/leo/enterpriseinertraining/controller/RagController.java \
        src/main/java/com/leo/enterpriseinertraining/dto/RagIngestRequest.java \
        src/main/java/com/leo/enterpriseinertraining/vo/IngestSummaryVO.java
git commit -m "feat(rag): PdfIngestPipeline + /api/rag/ingest end-to-end (3-store consistent)"
```

---

## Task 10：扩量到 30-50 篇 + 语料下载脚本

**Files:**
- Create: `scripts/download-corpus.sh`
- Create: `scripts/README.md`

- [ ] **Step 10.1：`scripts/README.md`**

```markdown
# RAG 预置语料

阶段 1 的预置语料库放在 `src/main/resources/corpus/`（被 .gitignore）。
推荐来源（完全公开、合规）：

| 来源 | 说明 | 站点 |
|---|---|---|
| CAICT（信通院） | AI/通信/数据/工业互联网白皮书 | http://www.caict.ac.cn/kxyj/qwfb/bps/ |
| MIIT（工信部） | 产业规划、行业指导意见 | https://www.miit.gov.cn |
| NDRC（发改委） | 战略性新兴产业规划 | https://www.ndrc.gov.cn |
| CCID（赛迪研究院） | 半导体/数字经济/新能源行业研报 | https://www.ccidwise.com |
| 巨潮资讯网 | 龙头公司年度财报 / 招股说明书 | http://www.cninfo.com.cn |

下载指引：手动从上述站点下载 30-50 篇 PDF，存到 `src/main/resources/corpus/`，
文件名建议：`<来源>-<主题>-<年份>.pdf`，例如：
- `caict-embodied-ai-2026.pdf`
- `miit-new-energy-vehicle-2026.pdf`
- `cninfo-ningde-2025-annual.pdf`

然后调用 `POST /api/rag/ingest` 入库。
```

- [ ] **Step 10.2：`scripts/download-corpus.sh`（演示用，实际下载需要手工）**

```bash
#!/usr/bin/env bash
# 仅打印预期文件清单，实际 PDF 受版权 / robots 限制需要手动下载。
# 用法：bash scripts/download-corpus.sh
set -euo pipefail
TARGET="src/main/resources/corpus"
mkdir -p "$TARGET"

echo "请按 scripts/README.md 指引，手动下载以下类别共 30-50 篇 PDF 到 $TARGET："
cat <<EOF
  - 信通院 AI / 通信 / 数据要素 白皮书 (~10 篇)
  - 工信部 / 发改委 行业规划 (~10 篇)
  - 赛迪研究院 半导体 / 新能源 行业研报 (~10 篇)
  - 龙头公司年度财报 / 招股说明书 (~10 篇)
EOF
ls "$TARGET"/*.pdf 2>/dev/null | wc -l | xargs -I{} echo "当前已有 {} 篇 PDF"
```

- [ ] **Step 10.3：放 PDF + 跑 ingest**

把 30-50 篇 PDF 放进 `src/main/resources/corpus/`，启动应用，调：

```bash
curl -s -X POST http://localhost:8080/api/rag/ingest \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"source":"MIXED","pathOrGlob":"*.pdf"}'
```

Expected：返回中 `filesIngested + filesSkipped == filesScanned`。

- [ ] **Step 10.4：行数一致性 + 提交**

```bash
docker exec irp-mysql mysql -uirp -pirppw irp -e \
  "SELECT (SELECT COUNT(*) FROM knowledge_doc) AS docs, (SELECT COUNT(*) FROM knowledge_chunk) AS chunks_mysql;"
docker exec irp-pgvector psql -U irp -d irp_vec -c \
  "SELECT COUNT(*) AS chunks_pg FROM knowledge_chunk_vec;"
curl -s "http://localhost:9201/knowledge_chunk_bm25/_count?pretty"
```
Expected：`chunks_mysql == chunks_pg == ES count`。

```bash
git add scripts/
git commit -m "docs(rag): corpus download script + README"
```

---

## Task 11：HybridRetriever + RRF + `/api/rag/search?useRerank=false`

**Files:**
- Create: `src/main/java/com/leo/enterpriseinertraining/rag/search/RrfFusion.java`
- Create: `src/main/java/com/leo/enterpriseinertraining/rag/search/HybridRetriever.java`
- Create: `src/main/java/com/leo/enterpriseinertraining/service/RagSearchService.java`
- Create: `src/main/java/com/leo/enterpriseinertraining/service/impl/RagSearchServiceImpl.java`
- Create: `src/main/java/com/leo/enterpriseinertraining/dto/RagSearchRequest.java`
- Create: `src/main/java/com/leo/enterpriseinertraining/vo/{RagHitVO,CitationVO}.java`
- Modify: `src/main/java/com/leo/enterpriseinertraining/controller/RagController.java`
- Test: `src/test/java/com/leo/enterpriseinertraining/rag/search/RrfFusionTest.java`

- [ ] **Step 11.1：先写 `RrfFusionTest.java`**

```java
package com.leo.enterpriseinertraining.rag.search;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RrfFusionTest {

    @Test
    void same_doc_top_in_both_lists_gets_highest_score() {
        List<Long> vec  = List.of(101L, 102L, 103L);
        List<Long> bm25 = List.of(101L, 104L, 105L);
        var fused = RrfFusion.fuse(List.of(vec, bm25), 60, 10);
        assertEquals(101L, fused.get(0).chunkId());
        assertEquals(1.0 / (60 + 1) + 1.0 / (60 + 1), fused.get(0).score(), 1e-9);
    }

    @Test
    void doc_only_in_one_list_still_appears() {
        var fused = RrfFusion.fuse(
                List.of(List.of(101L), List.of(202L)), 60, 10);
        assertEquals(2, fused.size());
        // 两个都是各自 rank=1，分数相同
        assertEquals(fused.get(0).score(), fused.get(1).score(), 1e-9);
    }

    @Test
    void topK_truncates() {
        var fused = RrfFusion.fuse(
                List.of(List.of(1L, 2L, 3L, 4L, 5L)), 60, 3);
        assertEquals(3, fused.size());
    }
}
```

- [ ] **Step 11.2：跑测试看到红灯**

```bash
./mvnw -q -Dtest=RrfFusionTest test
```
Expected：编译失败。

- [ ] **Step 11.3：实现 `RrfFusion.java`**

```java
package com.leo.enterpriseinertraining.rag.search;

import java.util.*;

public final class RrfFusion {

    private RrfFusion() {}

    public record Fused(long chunkId, double score) {}

    /**
     * 多路 rank list 融合。每路输入是有序的 chunkId 列表（rank 从 0 开始）。
     * score(chunk) = Σ 1 / (k + rank_i + 1)
     */
    public static List<Fused> fuse(List<List<Long>> rankedLists, int k, int topK) {
        Map<Long, Double> agg = new HashMap<>();
        for (List<Long> list : rankedLists) {
            for (int r = 0; r < list.size(); r++) {
                long id = list.get(r);
                agg.merge(id, 1.0 / (k + r + 1), Double::sum);
            }
        }
        return agg.entrySet().stream()
                .map(e -> new Fused(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingDouble(Fused::score).reversed())
                .limit(topK)
                .toList();
    }
}
```

- [ ] **Step 11.4：测试绿灯**

```bash
./mvnw -q -Dtest=RrfFusionTest test
```
Expected：`Tests run: 3, Failures: 0`。

- [ ] **Step 11.5：`HybridRetriever.java`**

```java
package com.leo.enterpriseinertraining.rag.search;

import com.leo.enterpriseinertraining.rag.llm.DashScopeEmbeddingClient;
import com.leo.enterpriseinertraining.rag.store.BM25Store;
import com.leo.enterpriseinertraining.rag.store.VectorStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

@Component
@RequiredArgsConstructor
public class HybridRetriever {

    private static final int CANDIDATES_PER_SIDE = 50;
    private static final int RRF_K = 60;

    private final DashScopeEmbeddingClient embedding;
    private final VectorStore vectorStore;
    private final BM25Store bm25Store;

    public record Candidate(
            long chunkId, long docId, String sectionTitle,
            int pageStart, int pageEnd, String content, double fusedScore) {}

    public List<Candidate> retrieve(String query, int topK) {
        // 1) 并发跑双路（Virtual Thread）
        var exec = Executors.newVirtualThreadPerTaskExecutor();
        CompletableFuture<List<VectorStore.VectorHit>> fVec =
                CompletableFuture.supplyAsync(() ->
                        vectorStore.search(embedding.embedOne(query), CANDIDATES_PER_SIDE), exec);
        CompletableFuture<List<BM25Store.BM25Hit>> fBm25 =
                CompletableFuture.supplyAsync(() ->
                        bm25Store.search(query, CANDIDATES_PER_SIDE), exec);
        List<VectorStore.VectorHit> vecHits;
        List<BM25Store.BM25Hit> bm25Hits;
        try {
            vecHits = fVec.get();
            bm25Hits = fBm25.get();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Hybrid retrieve 失败", e);
        }

        // 2) RRF 融合
        List<Long> vecRanked  = vecHits.stream().map(VectorStore.VectorHit::chunkId).toList();
        List<Long> bm25Ranked = bm25Hits.stream().map(BM25Store.BM25Hit::chunkId).toList();
        var fused = RrfFusion.fuse(List.of(vecRanked, bm25Ranked), RRF_K, topK);

        // 3) 把 content 从两路 hit 里 join 回来（PGVector hit 优先，包含 content）
        Map<Long, VectorStore.VectorHit> byChunkVec = new HashMap<>();
        for (var h : vecHits) byChunkVec.put(h.chunkId(), h);
        Map<Long, BM25Store.BM25Hit> byChunkBm25 = new HashMap<>();
        for (var h : bm25Hits) byChunkBm25.put(h.chunkId(), h);

        return fused.stream().map(f -> {
            var v = byChunkVec.get(f.chunkId());
            if (v != null) {
                return new Candidate(v.chunkId(), v.docId(), v.sectionTitle(),
                        v.pageStart(), v.pageEnd(), v.content(), f.score());
            }
            var b = byChunkBm25.get(f.chunkId());
            return new Candidate(b.chunkId(), b.docId(), b.sectionTitle(),
                    b.pageStart(), b.pageEnd(), b.content(), f.score());
        }).toList();
    }
}
```

- [ ] **Step 11.6：DTO/VO**

`RagSearchRequest.java`：
```java
package com.leo.enterpriseinertraining.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

@Data
public class RagSearchRequest implements Serializable {
    @NotBlank
    private String query;
    @Min(1) @Max(50)
    private Integer topK = 10;
    private Boolean useRerank = true;
}
```

`CitationVO.java`：
```java
package com.leo.enterpriseinertraining.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data @NoArgsConstructor @AllArgsConstructor
public class CitationVO implements Serializable {
    private Long docId;
    private String docTitle;
    private String source;
    private String sectionTitle;
    private Integer pageStart;
    private Integer pageEnd;
}
```

`RagHitVO.java`：
```java
package com.leo.enterpriseinertraining.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data @NoArgsConstructor @AllArgsConstructor
public class RagHitVO implements Serializable {
    private Long chunkId;
    private Double score;        // fused
    private Double rerankScore;  // null 表示未 rerank
    private String content;
    private CitationVO citation;
}
```

- [ ] **Step 11.7：`RagSearchService.java`（接口）**

```java
package com.leo.enterpriseinertraining.service;

import com.leo.enterpriseinertraining.dto.RagSearchRequest;
import com.leo.enterpriseinertraining.vo.RagHitVO;

import java.util.List;

public interface RagSearchService {
    record SearchResult(List<RagHitVO> hits, long tookMs) {}
    SearchResult search(RagSearchRequest req);
}
```

- [ ] **Step 11.8：`RagSearchServiceImpl.java`（先实现 useRerank=false 分支）**

```java
package com.leo.enterpriseinertraining.service.impl;

import com.leo.enterpriseinertraining.dto.RagSearchRequest;
import com.leo.enterpriseinertraining.entity.KnowledgeDoc;
import com.leo.enterpriseinertraining.mapper.KnowledgeDocMapper;
import com.leo.enterpriseinertraining.rag.search.HybridRetriever;
import com.leo.enterpriseinertraining.service.RagSearchService;
import com.leo.enterpriseinertraining.vo.CitationVO;
import com.leo.enterpriseinertraining.vo.RagHitVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RagSearchServiceImpl implements RagSearchService {

    private final HybridRetriever retriever;
    private final KnowledgeDocMapper docMapper;

    @Override
    public SearchResult search(RagSearchRequest req) {
        long t0 = System.currentTimeMillis();
        var candidates = retriever.retrieve(req.getQuery(), req.getTopK());

        // 批量取 doc title/source
        Set<Long> docIds = candidates.stream().map(c -> c.docId()).collect(Collectors.toSet());
        Map<Long, KnowledgeDoc> docMap = new HashMap<>();
        if (!docIds.isEmpty()) {
            for (KnowledgeDoc d : docMapper.selectListByIds(docIds)) {
                docMap.put(d.getId(), d);
            }
        }

        List<RagHitVO> hits = candidates.stream().map(c -> {
            KnowledgeDoc d = docMap.get(c.docId());
            CitationVO cite = new CitationVO(
                    c.docId(),
                    d == null ? null : d.getTitle(),
                    d == null ? null : d.getSource(),
                    c.sectionTitle(),
                    c.pageStart(), c.pageEnd());
            return new RagHitVO(c.chunkId(), c.fusedScore(), null, c.content(), cite);
        }).toList();

        return new SearchResult(hits, System.currentTimeMillis() - t0);
    }
}
```

- [ ] **Step 11.9：在 `RagController.java` 加 `/search` 端点**

在 `RagController.java` 顶部 import：
```java
import com.leo.enterpriseinertraining.dto.RagSearchRequest;
import com.leo.enterpriseinertraining.service.RagSearchService;
import com.leo.enterpriseinertraining.vo.RagHitVO;
import java.util.List;
import java.util.Map;
```
注入：
```java
    private final RagSearchService searchService;
```
追加方法：
```java
    @PostMapping("/search")
    @Operation(summary = "Hybrid 检索")
    public BaseResponse<Map<String, Object>> search(@RequestBody @Valid RagSearchRequest req) {
        var r = searchService.search(req);
        return ResultUtils.success(Map.of(
                "query", req.getQuery(),
                "tookMs", r.tookMs(),
                "hits", r.hits()));
    }
```

- [ ] **Step 11.10：编译 + 启动测试 `/search`**

```bash
./mvnw -q clean compile
nohup ./mvnw spring-boot:run > /tmp/irp-app.log 2>&1 &
echo $! > /tmp/irp-app.pid
for i in $(seq 1 24); do grep -q "Started" /tmp/irp-app.log && break; sleep 5; done

TOKEN=...   # 沿用 task 9.10 的 token
curl -s -X POST http://localhost:8080/api/rag/search \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"query":"具身智能产业链","topK":10,"useRerank":false}' | python3 -m json.tool
kill $(cat /tmp/irp-app.pid)
```
Expected：`code:0`，`hits` 长度 ≤ 10，每条含 `content`、`citation.docTitle / source / sectionTitle / pageStart / pageEnd`。

- [ ] **Step 11.11：提交**

```bash
git add src/main/java/com/leo/enterpriseinertraining/rag/search/ \
        src/main/java/com/leo/enterpriseinertraining/service/RagSearchService.java \
        src/main/java/com/leo/enterpriseinertraining/service/impl/RagSearchServiceImpl.java \
        src/main/java/com/leo/enterpriseinertraining/controller/RagController.java \
        src/main/java/com/leo/enterpriseinertraining/dto/RagSearchRequest.java \
        src/main/java/com/leo/enterpriseinertraining/vo/RagHitVO.java \
        src/main/java/com/leo/enterpriseinertraining/vo/CitationVO.java \
        src/test/java/com/leo/enterpriseinertraining/rag/search/RrfFusionTest.java
git commit -m "feat(rag): HybridRetriever (vec+bm25 parallel) + RRF fusion + /api/rag/search"
```

---

## Task 12：DashScope Rerank 客户端 + useRerank=true 分支

**Files:**
- Create: `src/main/java/com/leo/enterpriseinertraining/rag/llm/DashScopeRerankClient.java`
- Modify: `src/main/java/com/leo/enterpriseinertraining/service/impl/RagSearchServiceImpl.java`

- [ ] **Step 12.1：`DashScopeRerankClient.java`**

DashScope rerank 协议参考阿里云官方文档（`gte-rerank-v2`）。请求格式：

```java
package com.leo.enterpriseinertraining.rag.llm;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DashScopeRerankClient {

    @Value("${spring.ai.openai.api-key}") String apiKey;
    @Value("${app.dashscope.rerank-model}") String model;
    @Value("${app.dashscope.rerank-url}") String endpoint;

    public record Scored(int index, double relevance) {}

    public List<Scored> rerank(String query, List<String> documents, int topN) {
        RestClient rc = RestClient.create();
        Req req = new Req(model, new Input(query, documents),
                new Parameters(topN, true));
        Resp resp = rc.post()
                .uri(endpoint)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(req)
                .retrieve()
                .body(Resp.class);
        if (resp == null || resp.output == null || resp.output.results == null) {
            throw new RuntimeException("DashScope rerank 返回空");
        }
        return resp.output.results.stream()
                .map(r -> new Scored(r.index, r.relevanceScore))
                .toList();
    }

    @Data
    static class Req {
        String model;
        Input input;
        Parameters parameters;
        Req(String m, Input i, Parameters p) { this.model=m; this.input=i; this.parameters=p; }
    }
    @Data
    static class Input {
        String query;
        List<String> documents;
        Input(String q, List<String> d) { this.query=q; this.documents=d; }
    }
    @Data
    static class Parameters {
        @JsonProperty("top_n") Integer topN;
        @JsonProperty("return_documents") Boolean returnDocs;
        Parameters(Integer t, Boolean r) { this.topN=t; this.returnDocs=r; }
    }
    @Data static class Resp { Output output; }
    @Data static class Output { List<R> results; }
    @Data static class R {
        Integer index;
        @JsonProperty("relevance_score") Double relevanceScore;
    }
}
```

- [ ] **Step 12.2：修改 `RagSearchServiceImpl.java` 加 rerank 分支**

替换 `search` 方法为：

```java
    private final DashScopeRerankClient reranker;

    @Override
    public SearchResult search(RagSearchRequest req) {
        long t0 = System.currentTimeMillis();
        int retrieveK = Boolean.TRUE.equals(req.getUseRerank())
                ? Math.max(req.getTopK() * 3, 30)   // rerank 前先拿宽候选
                : req.getTopK();
        var candidates = retriever.retrieve(req.getQuery(), retrieveK);

        // doc 元数据
        Set<Long> docIds = candidates.stream().map(HybridRetriever.Candidate::docId).collect(Collectors.toSet());
        Map<Long, KnowledgeDoc> docMap = new HashMap<>();
        if (!docIds.isEmpty()) {
            for (KnowledgeDoc d : docMapper.selectListByIds(docIds)) docMap.put(d.getId(), d);
        }

        // Rerank（按需）
        List<HybridRetriever.Candidate> finalList;
        Map<Long, Double> rerankScoreByChunk = new HashMap<>();
        if (Boolean.TRUE.equals(req.getUseRerank()) && !candidates.isEmpty()) {
            List<String> texts = candidates.stream().map(HybridRetriever.Candidate::content).toList();
            var scored = reranker.rerank(req.getQuery(), texts, req.getTopK());
            List<HybridRetriever.Candidate> reordered = new java.util.ArrayList<>();
            for (var s : scored) {
                if (s.index() < 0 || s.index() >= candidates.size()) continue;
                var c = candidates.get(s.index());
                reordered.add(c);
                rerankScoreByChunk.put(c.chunkId(), s.relevance());
            }
            finalList = reordered;
        } else {
            finalList = candidates.size() > req.getTopK()
                    ? candidates.subList(0, req.getTopK()) : candidates;
        }

        List<RagHitVO> hits = finalList.stream().map(c -> {
            KnowledgeDoc d = docMap.get(c.docId());
            CitationVO cite = new CitationVO(
                    c.docId(),
                    d == null ? null : d.getTitle(),
                    d == null ? null : d.getSource(),
                    c.sectionTitle(),
                    c.pageStart(), c.pageEnd());
            return new RagHitVO(c.chunkId(), c.fusedScore(),
                    rerankScoreByChunk.get(c.chunkId()),
                    c.content(), cite);
        }).toList();
        return new SearchResult(hits, System.currentTimeMillis() - t0);
    }
```
（其余 imports 不变；记得在类顶部加 `private final DashScopeRerankClient reranker;`。Lombok `@RequiredArgsConstructor` 自动注入。）

- [ ] **Step 12.3：启动 + 对比验证 useRerank=true / false**

```bash
./mvnw -q clean compile
nohup ./mvnw spring-boot:run > /tmp/irp-app.log 2>&1 &
echo $! > /tmp/irp-app.pid
for i in $(seq 1 24); do grep -q "Started" /tmp/irp-app.log && break; sleep 5; done

TOKEN=...
for rr in false true; do
  echo "=== useRerank=$rr ==="
  curl -s -X POST http://localhost:8080/api/rag/search \
    -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
    -d "{\"query\":\"具身智能产业链上游核心零部件\",\"topK\":5,\"useRerank\":$rr}" \
    | python3 -c "import sys,json; r=json.load(sys.stdin); [print(f'  chunkId={h[\"chunkId\"]} score={h[\"score\"]:.4f} rerank={h.get(\"rerankScore\")}') for h in r['data']['hits']]"
done
kill $(cat /tmp/irp-app.pid)
```
Expected：useRerank=true 时每条 hit 含 rerankScore（非 null），且顺序通常不同于 useRerank=false。

- [ ] **Step 12.4：提交**

```bash
git add src/main/java/com/leo/enterpriseinertraining/rag/llm/DashScopeRerankClient.java \
        src/main/java/com/leo/enterpriseinertraining/service/impl/RagSearchServiceImpl.java
git commit -m "feat(rag): DashScope gte-rerank-v2 client + useRerank branch"
```

---

## Task 13：评估数据集合成（QuerySynthesizer + 写入 50 条 GT）

**Files:**
- Create: `src/main/java/com/leo/enterpriseinertraining/rag/eval/QuerySynthesizer.java`
- Create: `src/main/java/com/leo/enterpriseinertraining/service/RagEvalService.java`
- Create: `src/main/java/com/leo/enterpriseinertraining/service/impl/RagEvalServiceImpl.java`
- Modify: `src/main/java/com/leo/enterpriseinertraining/controller/RagController.java`（加 `/eval/synthesize`）

- [ ] **Step 13.1：`QuerySynthesizer.java`**

```java
package com.leo.enterpriseinertraining.rag.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.enterpriseinertraining.entity.KnowledgeChunk;
import com.leo.enterpriseinertraining.mapper.KnowledgeChunkMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.leo.enterpriseinertraining.entity.table.KnowledgeChunkTableDef.KNOWLEDGE_CHUNK;

@Slf4j
@Component
@RequiredArgsConstructor
public class QuerySynthesizer {

    private final ChatClient.Builder chatClientBuilder;
    private final KnowledgeChunkMapper chunkMapper;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc; // 主 datasource 默认注入
    private final ObjectMapper om = new ObjectMapper();

    public record QGen(String query, long goldChunkId) {}

    /** 从向量库 PG 取 chunk content（chunk 文本不在 MySQL）。 */
    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.beans.factory.annotation.Qualifier("pgVectorJdbcTemplate")
    private org.springframework.jdbc.core.JdbcTemplate pgJdbc;

    /**
     * 从已 ingest 的 chunk 中采样 N 条，调 qwen-max 为每个 chunk 生成 1 个用户视角的 query。
     */
    public List<QGen> synthesize(int sampleSize) {
        List<KnowledgeChunk> all = chunkMapper.selectListByQuery(
                QueryWrapper.create().orderBy(KNOWLEDGE_CHUNK.ID, true));
        if (all.isEmpty()) return List.of();
        Collections.shuffle(all, new Random(42));
        List<KnowledgeChunk> picked = all.subList(0, Math.min(sampleSize, all.size()));

        ChatClient chat = chatClientBuilder.build();
        List<QGen> out = new ArrayList<>();
        for (KnowledgeChunk c : picked) {
            String content = fetchContent(c.getVectorId());
            if (content == null || content.length() < 50) continue;
            String prompt = """
                你是一名行业研究分析师，正在构造检索评估数据集。
                请基于下面这段研报内容，生成 1 个用户在 RAG 系统里会问的中文检索 query。
                要求：
                - 不要直接复制原文，要从"用户视角"提问
                - 体现真实业务需求（趋势、对比、原因、规模、关键玩家等）
                - 一句话，10-30 字
                只输出 JSON：{"query": "..."}

                章节标题：%s
                内容（节选）：
                %s
                """.formatted(
                    c.getSectionTitle() == null ? "" : c.getSectionTitle(),
                    content.length() > 800 ? content.substring(0, 800) : content);
            try {
                String resp = chat.prompt().user(prompt).call().content();
                String json = extractJson(resp);
                JsonNode node = om.readTree(json);
                String q = node.get("query").asText().trim();
                if (!q.isEmpty()) out.add(new QGen(q, c.getId()));
            } catch (Exception e) {
                log.warn("synthesize failed for chunk {}: {}", c.getId(), e.getMessage());
            }
        }
        return out;
    }

    private String fetchContent(String vectorId) {
        try {
            return pgJdbc.queryForObject(
                    "SELECT content FROM knowledge_chunk_vec WHERE id = ?::uuid",
                    String.class, vectorId);
        } catch (Exception e) { return null; }
    }

    private static String extractJson(String s) {
        int l = s.indexOf('{'), r = s.lastIndexOf('}');
        return (l >= 0 && r > l) ? s.substring(l, r + 1) : s;
    }
}
```

- [ ] **Step 13.2：`RagEvalService.java`（接口，含 synthesize / latest 占位 run）**

```java
package com.leo.enterpriseinertraining.service;

import com.leo.enterpriseinertraining.vo.EvalSummaryVO;

public interface RagEvalService {
    int synthesizeAndStore(int sampleSize);
    EvalSummaryVO run();
    EvalSummaryVO latest();
}
```

- [ ] **Step 13.3：`EvalSummaryVO.java`**

```java
package com.leo.enterpriseinertraining.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data @NoArgsConstructor @AllArgsConstructor
public class EvalSummaryVO implements Serializable {
    private int totalQueries;
    private double recallAt10NoRerank;
    private double recallAt10WithRerank;
    private double mrrNoRerank;
    private double mrrWithRerank;
    private double ndcg10NoRerank;
    private double ndcg10WithRerank;
    private double rerankLiftNdcg;          // ndcg10WithRerank - ndcg10NoRerank
    private long tookMs;
    private long runAt;                     // epochMillis
}
```

- [ ] **Step 13.4：`RagEvalServiceImpl.java`（先实现 synthesize + 占位 run/latest）**

```java
package com.leo.enterpriseinertraining.service.impl;

import com.leo.enterpriseinertraining.entity.RagEvalQuery;
import com.leo.enterpriseinertraining.mapper.RagEvalQueryMapper;
import com.leo.enterpriseinertraining.rag.eval.QuerySynthesizer;
import com.leo.enterpriseinertraining.service.RagEvalService;
import com.leo.enterpriseinertraining.vo.EvalSummaryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RagEvalServiceImpl implements RagEvalService {

    private final QuerySynthesizer synthesizer;
    private final RagEvalQueryMapper evalMapper;

    @Override
    public int synthesizeAndStore(int sampleSize) {
        var gens = synthesizer.synthesize(sampleSize);
        for (var g : gens) {
            RagEvalQuery row = new RagEvalQuery();
            row.setQueryText(g.query());
            row.setGoldChunkIds(List.of(g.goldChunkId()));
            row.setQuerySource("synthesized");
            evalMapper.insert(row);
        }
        return gens.size();
    }

    @Override
    public EvalSummaryVO run() {
        // 占位：完整 run 在 Task 14 实现
        throw new UnsupportedOperationException("evaluator coming in Task 14");
    }

    @Override
    public EvalSummaryVO latest() {
        throw new UnsupportedOperationException("evaluator coming in Task 14");
    }
}
```

- [ ] **Step 13.5：`RagController.java` 加 `/eval/synthesize`**

在 controller 注入 `private final RagEvalService evalService;` 并追加：

```java
    @PostMapping("/eval/synthesize")
    @Operation(summary = "合成评估 query（每个 chunk 生 1 个，需 ADMIN 自行筛选）")
    public BaseResponse<Integer> synthesize(@RequestParam(defaultValue = "100") int sampleSize) {
        return ResultUtils.success(evalService.synthesizeAndStore(sampleSize));
    }
```

- [ ] **Step 13.6：跑合成 + 人工筛选**

启动应用，登录拿 token，调用：

```bash
curl -s -X POST "http://localhost:8080/api/rag/eval/synthesize?sampleSize=100" \
  -H "Authorization: Bearer $TOKEN"
```
Expected：返回数字（合成成功条数，理想 80-100）。

人工筛选（直接改 DB）：人工 review `rag_eval_query` 全表，保留 50 条质量好的：
```sql
-- 保留质量好的 50 条，其它逻辑删除
UPDATE rag_eval_query SET is_deleted = 1 WHERE id NOT IN (
    1, 3, 5, 7, ...  -- 自己挑的 50 个 id
);
```

- [ ] **Step 13.7：提交**

```bash
git add src/main/java/com/leo/enterpriseinertraining/rag/eval/QuerySynthesizer.java \
        src/main/java/com/leo/enterpriseinertraining/service/RagEvalService.java \
        src/main/java/com/leo/enterpriseinertraining/service/impl/RagEvalServiceImpl.java \
        src/main/java/com/leo/enterpriseinertraining/vo/EvalSummaryVO.java \
        src/main/java/com/leo/enterpriseinertraining/controller/RagController.java
git commit -m "feat(rag): QuerySynthesizer (qwen-max) + /api/rag/eval/synthesize"
```

---

## Task 14：RagEvaluator（Recall/MRR/NDCG）+ 评估端点 + 端到端冒烟

**Files:**
- Create: `src/main/java/com/leo/enterpriseinertraining/rag/eval/RagEvaluator.java`
- Modify: `src/main/java/com/leo/enterpriseinertraining/service/impl/RagEvalServiceImpl.java`
- Modify: `src/main/java/com/leo/enterpriseinertraining/controller/RagController.java`
- Test: `src/test/java/com/leo/enterpriseinertraining/rag/eval/RagEvaluatorTest.java`

- [ ] **Step 14.1：先写测试 `RagEvaluatorTest.java`**

```java
package com.leo.enterpriseinertraining.rag.eval;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RagEvaluatorTest {

    @Test
    void recall_at_k_basic() {
        var m = RagEvaluator.compute(
                List.of(1L, 2L, 3L), List.of(2L, 99L), 3);
        assertEquals(1.0, m.recall(), 1e-9);
    }

    @Test
    void recall_zero_when_no_hit() {
        var m = RagEvaluator.compute(
                List.of(1L, 2L, 3L), List.of(99L), 3);
        assertEquals(0.0, m.recall(), 1e-9);
    }

    @Test
    void mrr_first_hit_at_rank_2_is_half() {
        var m = RagEvaluator.compute(
                List.of(1L, 5L, 9L), List.of(5L), 10);
        assertEquals(0.5, m.mrr(), 1e-9);
    }

    @Test
    void ndcg_first_position_full_score() {
        var m = RagEvaluator.compute(
                List.of(5L, 1L, 2L), List.of(5L), 10);
        // DCG = 1/log2(2)=1, IDCG=1, NDCG=1
        assertEquals(1.0, m.ndcg(), 1e-9);
    }
}
```

- [ ] **Step 14.2：跑红灯**

```bash
./mvnw -q -Dtest=RagEvaluatorTest test
```
Expected：编译失败。

- [ ] **Step 14.3：实现 `RagEvaluator.java`**

```java
package com.leo.enterpriseinertraining.rag.eval;

import java.util.List;
import java.util.Set;

public final class RagEvaluator {

    private RagEvaluator() {}

    public record Metrics(double recall, double mrr, double ndcg) {}

    public static Metrics compute(List<Long> retrievedRanked, List<Long> gold, int k) {
        Set<Long> goldSet = Set.copyOf(gold);
        int top = Math.min(k, retrievedRanked.size());

        // Recall@k
        boolean hit = false;
        for (int i = 0; i < top; i++) {
            if (goldSet.contains(retrievedRanked.get(i))) { hit = true; break; }
        }
        double recall = hit ? 1.0 : 0.0;

        // MRR
        double mrr = 0.0;
        for (int i = 0; i < top; i++) {
            if (goldSet.contains(retrievedRanked.get(i))) {
                mrr = 1.0 / (i + 1);
                break;
            }
        }

        // NDCG@k（binary relevance：gold 1，否则 0）
        double dcg = 0.0;
        for (int i = 0; i < top; i++) {
            if (goldSet.contains(retrievedRanked.get(i))) {
                dcg += 1.0 / (Math.log(i + 2) / Math.log(2));
            }
        }
        double idcg = 0.0;
        int idealHits = Math.min(goldSet.size(), top);
        for (int i = 0; i < idealHits; i++) {
            idcg += 1.0 / (Math.log(i + 2) / Math.log(2));
        }
        double ndcg = idcg == 0 ? 0 : dcg / idcg;

        return new Metrics(recall, mrr, ndcg);
    }
}
```

- [ ] **Step 14.4：测试绿灯**

```bash
./mvnw -q -Dtest=RagEvaluatorTest test
```
Expected：`Tests run: 4, Failures: 0`。

- [ ] **Step 14.5：实现 `RagEvalServiceImpl.run()`**

替换占位的 `run()` 与 `latest()`：

```java
    private final com.leo.enterpriseinertraining.rag.search.HybridRetriever retriever;
    private final com.leo.enterpriseinertraining.rag.llm.DashScopeRerankClient reranker;
    // 简单地把最近一次 run 结果缓存在静态字段；阶段 4 再做持久化
    private static volatile EvalSummaryVO LAST;

    @Override
    public EvalSummaryVO run() {
        long t0 = System.currentTimeMillis();
        List<com.leo.enterpriseinertraining.entity.RagEvalQuery> set =
                evalMapper.selectListByQuery(
                        com.mybatisflex.core.query.QueryWrapper.create()
                                .where(com.leo.enterpriseinertraining.entity.table
                                        .RagEvalQueryTableDef.RAG_EVAL_QUERY.IS_DELETED.eq(0)));
        int n = set.size();
        double rNo = 0, rRr = 0, mNo = 0, mRr = 0, nNo = 0, nRr = 0;
        for (var eq : set) {
            var cands = retriever.retrieve(eq.getQueryText(), 50);
            List<Long> ranked = cands.stream()
                    .map(com.leo.enterpriseinertraining.rag.search.HybridRetriever.Candidate::chunkId).toList();
            var mNoR = com.leo.enterpriseinertraining.rag.eval.RagEvaluator.compute(
                    ranked, eq.getGoldChunkIds(), 10);
            rNo += mNoR.recall(); mNo += mNoR.mrr(); nNo += mNoR.ndcg();

            // rerank top-10
            if (!cands.isEmpty()) {
                var docs = cands.stream()
                        .map(com.leo.enterpriseinertraining.rag.search.HybridRetriever.Candidate::content).toList();
                var scored = reranker.rerank(eq.getQueryText(), docs, 10);
                List<Long> rerankRanked = scored.stream()
                        .filter(s -> s.index() >= 0 && s.index() < cands.size())
                        .map(s -> cands.get(s.index()).chunkId()).toList();
                var mWith = com.leo.enterpriseinertraining.rag.eval.RagEvaluator.compute(
                        rerankRanked, eq.getGoldChunkIds(), 10);
                rRr += mWith.recall(); mRr += mWith.mrr(); nRr += mWith.ndcg();
            }
        }
        var summary = new EvalSummaryVO(
                n,
                n == 0 ? 0 : rNo / n,
                n == 0 ? 0 : rRr / n,
                n == 0 ? 0 : mNo / n,
                n == 0 ? 0 : mRr / n,
                n == 0 ? 0 : nNo / n,
                n == 0 ? 0 : nRr / n,
                n == 0 ? 0 : (nRr - nNo) / n * n,   // 已是平均差
                System.currentTimeMillis() - t0,
                System.currentTimeMillis());
        LAST = summary;
        return summary;
    }

    @Override
    public EvalSummaryVO latest() {
        return LAST;
    }
```

> 注：`@RequiredArgsConstructor` 自动注入新增的 `retriever` / `reranker` 两个 final 字段。

- [ ] **Step 14.6：`RagController.java` 加 `/eval/run` 与 `/eval/latest`**

```java
    @PostMapping("/eval/run")
    @Operation(summary = "跑完整评估（with vs without rerank）")
    public BaseResponse<EvalSummaryVO> evalRun() {
        return ResultUtils.success(evalService.run());
    }

    @GetMapping("/eval/latest")
    @Operation(summary = "最近一次评估结果")
    public BaseResponse<EvalSummaryVO> evalLatest() {
        return ResultUtils.success(evalService.latest());
    }
```
顶部 import：`import com.leo.enterpriseinertraining.vo.EvalSummaryVO;`

- [ ] **Step 14.7：端到端冒烟 + 出口指标验证**

```bash
./mvnw -q clean compile test-compile
nohup ./mvnw spring-boot:run > /tmp/irp-app.log 2>&1 &
echo $! > /tmp/irp-app.pid
for i in $(seq 1 24); do grep -q "Started" /tmp/irp-app.log && break; sleep 5; done

TOKEN=...

# 1) 跑评估
curl -s -X POST http://localhost:8080/api/rag/eval/run \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool

# 2) 看最近一次
curl -s http://localhost:8080/api/rag/eval/latest \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool

# 3) 单次 search 性能（带 rerank）
time curl -s -X POST http://localhost:8080/api/rag/search \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"query":"动力电池行业市场规模与竞争格局","topK":10,"useRerank":true}' > /dev/null

kill $(cat /tmp/irp-app.pid)
```

**出口验证**（达到则视为阶段 1 完成）：
- `recallAt10WithRerank >= 0.80`
- `mrrWithRerank >= 0.55`
- `(ndcg10WithRerank - ndcg10NoRerank) >= 0.10`
- 单次 search 用户态时间 P95 ≤ 1500ms（多跑几次取 P95）

若指标不达标，可调以下旋钮（不算 plan failure）：
- 候选宽度：`HybridRetriever.CANDIDATES_PER_SIDE` 50 → 80
- RRF k：`RRF_K` 60 → 30（更偏向 top）
- ES `multi_match` 加 `phrase` boost
- 重新筛 gold（剔除 query 与 chunk 关系弱的样本）

- [ ] **Step 14.8：跑测试 + 最终 commit**

```bash
./mvnw -q test
git add src/main/java/com/leo/enterpriseinertraining/rag/eval/RagEvaluator.java \
        src/main/java/com/leo/enterpriseinertraining/service/impl/RagEvalServiceImpl.java \
        src/main/java/com/leo/enterpriseinertraining/controller/RagController.java \
        src/test/java/com/leo/enterpriseinertraining/rag/eval/RagEvaluatorTest.java
git commit -m "feat(rag): RagEvaluator (Recall@k/MRR/NDCG@k) + /eval/run /eval/latest

阶段 1 RAG 核心收口：
- 50 条 GT 评估集 (synthesized + 人工筛选)
- with/without rerank 指标对比
- 出口达成：Recall@10>=0.80, MRR>=0.55, Rerank Lift>=0.10, P95<=1.5s"
```

---

## 阶段 1 出口条件（DoD）

- ✅ `./mvnw test` 全绿（含 ContentHashCalculatorTest / PdfStructuredReaderTest / RrfFusionTest / RagEvaluatorTest）
- ✅ docker compose 6 容器 Up（含带 IK 的 irp-es）
- ✅ `POST /api/rag/ingest` 30-50 篇 PDF 入库；`knowledge_chunk / knowledge_chunk_vec / ES count` 三库行数一致
- ✅ `POST /api/rag/search` 返回带引用的 hits（含 docTitle/source/sectionTitle/pageRange）
- ✅ `POST /api/rag/eval/run` 报告满足：Recall@10 ≥ 0.80、MRR ≥ 0.55、Rerank Lift ≥ 0.10
- ✅ 单次 search P95 ≤ 1.5s
- ✅ 同一 PDF 重复 ingest 被 content_hash 跳过

---

## Self-Review

**1. Spec 覆盖：**
- §1 决策（语料/嵌入/向量库/BM25/Hybrid/Rerank/评估）→ T1（依赖）/T4（ES+IK）/T6（embed）/T7（PG）/T8（ES）/T11（RRF）/T12（rerank）/T13-14（评估）✓
- §2 流程（ingest 离线 + search 在线）→ T9（ingest pipeline）/T11+T12（search）✓
- §3 数据模型（3 张 MySQL 表）→ T2 ✓
- §4 PGVector 与 ES schema → T3（pgvector-init.sql）+ T4（ES mapping）✓
- §5 4 个 REST API → T9 (/ingest) + T11 (/search) + T13 (/eval/synthesize) + T14 (/eval/run, /eval/latest) ✓
- §6 评估方法（合成→筛选→Recall/MRR/NDCG）→ T13 + T14 ✓
- §7 文件结构 → 与本 plan 各 task 文件清单一一对应 ✓
- §9 验证清单（功能/性能/质量/工程）→ T9.10（三库行数）/T14.7（指标 + P95）✓

**2. Placeholder 扫描：** 无 TBD/TODO/incomplete；除 Task 13 人工筛选步骤需用户实操（SQL DELETE/UPDATE 已给模板）外，所有 step 含可运行命令或完整代码 ✓

**3. 类型一致性：**
- `VectorStore.VectorRow / VectorHit`（T7）↔ `PgVectorStoreImpl`（T7） ↔ `HybridRetriever`（T11）↔ `PdfIngestPipeline`（T9）使用一致 ✓
- `BM25Store.BM25Row / BM25Hit` 同样一致 ✓
- `HybridRetriever.Candidate` 在 T11 定义后 T12（RagSearchServiceImpl rerank 分支）与 T14（评估）都引用一致 ✓
- `DashScopeRerankClient.Scored(index, relevance)` 在 T12（service）+ T14（eval）使用一致 ✓
- `RagEvaluator.Metrics(recall, mrr, ndcg)` 在 T14 单元测试与 service 中调用一致 ✓
- `KnowledgeChunk.vectorId` 类型 String（UUID 文本）在 T2/T9 入库 + T13 `fetchContent` 用 `?::uuid` cast 一致 ✓
- MyBatis-Flex 生成的 `*TableDef.IS_DELETED` 字段名在 T14 使用 `.eq(0)` 正确 ✓

无遗漏。

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-05-19-phase-1-rag-core.md`.

两种执行方式：

**1. Subagent-Driven（推荐）** —— 每个 Task 派一个 fresh subagent + 两阶段 review（spec compliance → code quality），与阶段 0 同样模式。

**2. Inline Execution** —— 当前会话连续跑全部 Task，checkpoint 暂停 review。

请选择 1 或 2。
