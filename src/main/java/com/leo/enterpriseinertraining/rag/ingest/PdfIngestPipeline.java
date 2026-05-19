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
