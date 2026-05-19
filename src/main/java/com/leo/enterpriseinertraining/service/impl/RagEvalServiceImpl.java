package com.leo.enterpriseinertraining.service.impl;

import com.leo.enterpriseinertraining.entity.RagEvalQuery;
import com.leo.enterpriseinertraining.mapper.RagEvalQueryMapper;
import com.leo.enterpriseinertraining.rag.eval.QuerySynthesizer;
import com.leo.enterpriseinertraining.rag.eval.RagEvaluator;
import com.leo.enterpriseinertraining.rag.llm.DashScopeRerankClient;
import com.leo.enterpriseinertraining.rag.search.HybridRetriever;
import com.leo.enterpriseinertraining.service.RagEvalService;
import com.leo.enterpriseinertraining.vo.EvalSummaryVO;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.leo.enterpriseinertraining.entity.table.RagEvalQueryTableDef.RAG_EVAL_QUERY;

@Service
@RequiredArgsConstructor
public class RagEvalServiceImpl implements RagEvalService {

    private final QuerySynthesizer synthesizer;
    private final RagEvalQueryMapper evalMapper;
    private final HybridRetriever retriever;
    private final DashScopeRerankClient reranker;

    private static volatile EvalSummaryVO LAST;

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
        long t0 = System.currentTimeMillis();
        List<RagEvalQuery> set = evalMapper.selectListByQuery(
                QueryWrapper.create().where(RAG_EVAL_QUERY.IS_DELETED.eq(0)));
        int n = set.size();
        double rNo = 0, rRr = 0, mNo = 0, mRr = 0, nNo = 0, nRr = 0;
        for (var eq : set) {
            var cands = retriever.retrieve(eq.getQueryText(), 50);
            List<Long> ranked = cands.stream()
                    .map(HybridRetriever.Candidate::chunkId).toList();
            var mNoR = RagEvaluator.compute(ranked, eq.getGoldChunkIds(), 10);
            rNo += mNoR.recall(); mNo += mNoR.mrr(); nNo += mNoR.ndcg();

            if (!cands.isEmpty()) {
                var docs = cands.stream().map(HybridRetriever.Candidate::content).toList();
                var scored = reranker.rerank(eq.getQueryText(), docs, 10);
                List<Long> rerankRanked = scored.stream()
                        .filter(s -> s.index() >= 0 && s.index() < cands.size())
                        .map(s -> cands.get(s.index()).chunkId()).toList();
                var mWith = RagEvaluator.compute(rerankRanked, eq.getGoldChunkIds(), 10);
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
                n == 0 ? 0 : (nRr - nNo) / n,
                System.currentTimeMillis() - t0,
                System.currentTimeMillis());
        LAST = summary;
        return summary;
    }

    @Override
    public EvalSummaryVO latest() {
        return LAST;
    }
}
