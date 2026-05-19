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
        throw new UnsupportedOperationException("evaluator coming in Task 14");
    }

    @Override
    public EvalSummaryVO latest() {
        throw new UnsupportedOperationException("evaluator coming in Task 14");
    }
}
