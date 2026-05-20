package com.leo.enterpriseinertraining.workflow;

import com.leo.enterpriseinertraining.entity.WorkflowSubtask;
import com.leo.enterpriseinertraining.mapper.WorkflowSubtaskMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static com.leo.enterpriseinertraining.entity.table.WorkflowSubtaskTableDef.WORKFLOW_SUBTASK;

/**
 * Fanout/Join 状态追踪：
 *
 * <p>{@link #markCompletedAndCheckLast(long, int, String)}：把单个子任务标 DONE，
 * 再查同 task 是否还有非 DONE 行；返回 true 即"我是最后一个完成的"，调用方应触发下游节点。</p>
 *
 * <p>整体在事务里跑：update 与 count 之间不会有其它子任务竞争 race（依赖 MySQL 默认 RR 隔离 + 唯一索引）。
 * 实际并发场景下两个 worker 同时标完最后两条 → 都查到 count=0 → 都触发下游 —— 这种"双发"风险通过
 * 下游 consumer 的幂等去重容忍。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JoinTracker {

    private final WorkflowSubtaskMapper mapper;

    @Transactional
    public boolean markCompletedAndCheckLast(long taskId, int subIndex, String resultJson) {
        // 1) 取自己这一行
        List<WorkflowSubtask> rows = mapper.selectListByQuery(
                QueryWrapper.create()
                        .where(WORKFLOW_SUBTASK.TASK_ID.eq(taskId))
                        .and(WORKFLOW_SUBTASK.SUB_INDEX.eq(subIndex)));
        if (rows.isEmpty()) {
            throw new RuntimeException("找不到子任务 taskId=" + taskId + " subIndex=" + subIndex);
        }
        WorkflowSubtask row = rows.get(0);
        row.setStatus("DONE");
        row.setResultJson(resultJson);
        row.setFinishedAt(LocalDateTime.now());
        mapper.update(row);

        // 2) 查同 task 还有几行非 DONE
        long remaining = mapper.selectCountByQuery(
                QueryWrapper.create()
                        .where(WORKFLOW_SUBTASK.TASK_ID.eq(taskId))
                        .and(WORKFLOW_SUBTASK.STATUS.ne("DONE")));

        boolean isLast = remaining == 0;
        log.info("[Join] task={} subIndex={} done; remaining={}; isLast={}", taskId, subIndex, remaining, isLast);
        return isLast;
    }

    /** 把单子任务标 FAILED（不触发下游；workflow 整体标记 FAILED 由调用方决定）。 */
    @Transactional
    public void markFailed(long taskId, int subIndex, String errorMessage) {
        List<WorkflowSubtask> rows = mapper.selectListByQuery(
                QueryWrapper.create()
                        .where(WORKFLOW_SUBTASK.TASK_ID.eq(taskId))
                        .and(WORKFLOW_SUBTASK.SUB_INDEX.eq(subIndex)));
        if (rows.isEmpty()) return;
        WorkflowSubtask row = rows.get(0);
        row.setStatus("FAILED");
        row.setErrorMessage(errorMessage);
        row.setFinishedAt(LocalDateTime.now());
        mapper.update(row);
    }
}
