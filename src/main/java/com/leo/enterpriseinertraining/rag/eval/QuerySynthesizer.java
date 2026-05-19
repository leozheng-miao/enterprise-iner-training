package com.leo.enterpriseinertraining.rag.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.enterpriseinertraining.entity.KnowledgeChunk;
import com.leo.enterpriseinertraining.mapper.KnowledgeChunkMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.leo.enterpriseinertraining.entity.table.KnowledgeChunkTableDef.KNOWLEDGE_CHUNK;

@Slf4j
@Component
@RequiredArgsConstructor
public class QuerySynthesizer {

    private final ChatClient.Builder chatClientBuilder;
    private final KnowledgeChunkMapper chunkMapper;
    private final ObjectMapper om = new ObjectMapper();

    @Autowired
    @Qualifier("pgVectorJdbcTemplate")
    private JdbcTemplate pgJdbc;

    public record QGen(String query, long goldChunkId) {}

    /**
     * 从已 ingest 的 chunk 中采样 sampleSize 条，调 qwen-max 为每个 chunk 生成 1 个用户视角的 query。
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
