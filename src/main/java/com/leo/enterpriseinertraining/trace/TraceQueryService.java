package com.leo.enterpriseinertraining.trace;

import com.leo.enterpriseinertraining.entity.WorkflowNodeRun;
import com.leo.enterpriseinertraining.mapper.WorkflowNodeRunMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.leo.enterpriseinertraining.entity.table.WorkflowNodeRunTableDef.WORKFLOW_NODE_RUN;

@Service
@RequiredArgsConstructor
public class TraceQueryService {

    private final WorkflowNodeRunMapper mapper;

    public List<WorkflowNodeRun> findByTaskId(long taskId) {
        return mapper.selectListByQuery(
                QueryWrapper.create()
                        .where(WORKFLOW_NODE_RUN.TASK_ID.eq(taskId))
                        .orderBy(WORKFLOW_NODE_RUN.STEP_SEQ, true));
    }
}
