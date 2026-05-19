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
 *       每个 {@link Document} 的 metadata 含 title / page_number / end_page_number。</li>
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
        PagePdfDocumentReader page = new PagePdfDocumentReader(
                pdf, PdfDocumentReaderConfig.defaultConfig());
        return page.get();
    }
}
