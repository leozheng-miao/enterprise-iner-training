package com.leo.enterpriseinertraining.trace;

import com.leo.enterpriseinertraining.entity.ReportTask;
import com.leo.enterpriseinertraining.mapper.ReportTaskMapper;
import com.leo.enterpriseinertraining.mapper.WorkflowNodeRunMapper;
import com.leo.enterpriseinertraining.vo.AgentCostVO;
import com.leo.enterpriseinertraining.vo.ModelCostVO;
import com.leo.enterpriseinertraining.vo.PlatformOverviewVO;
import com.leo.enterpriseinertraining.vo.TaskBriefVO;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.leo.enterpriseinertraining.entity.table.ReportTaskTableDef.REPORT_TASK;
import static com.leo.enterpriseinertraining.entity.table.WorkflowNodeRunTableDef.WORKFLOW_NODE_RUN;
import static com.mybatisflex.core.query.QueryMethods.count;
import static com.mybatisflex.core.query.QueryMethods.sum;

/**
 * 平台可观测中台：基于 {@code workflow_node_run} + {@code report_task} 做聚合统计。
 *
 * <p>提供三类视图：<br>
 * - {@link #overview()}：平台总览（任务量、成功率、Token 成本、端到端耗时）<br>
 * - {@link #costByModel()} / {@link #costByAgent()}：Token 成本看板，按模型 / 角色拆分<br>
 * - {@link #tasks(String, int, int)}：任务列表分页查询。</p>
 *
 * <p>成本折算见 {@link ModelPricing}。聚合一次性 {@code GROUP BY (model, agent_role)}
 * 取回明细行，再在内存里做 model 维度与 agent 维度两次汇总，避免多次扫表。</p>
 */
@Service
@RequiredArgsConstructor
public class AdminStatsService {

    private final WorkflowNodeRunMapper nodeRunMapper;
    private final ReportTaskMapper taskMapper;

    /** {@code GROUP BY (model, agent_role)} 的聚合明细行。 */
    @Data
    public static class NodeAggRow {
        private String model;
        private String agentRole;
        private Long calls;
        private Long tokensIn;
        private Long tokensOut;
        private Long latencySum;
    }

    // ── 平台总览 ──────────────────────────────────────────────

    public PlatformOverviewVO overview() {
        long total  = taskMapper.selectCountByQuery(QueryWrapper.create());
        long done   = countTask("DONE");
        long failed = countTask("FAILED");
        long running = Math.max(0, total - done - failed);
        long finished = done + failed;
        double successRate = finished == 0 ? 0.0 : round((double) done / finished, 4);

        long totalNodeRuns = nodeRunMapper.selectCountByQuery(QueryWrapper.create());
        long errorNodeRuns = nodeRunMapper.selectCountByQuery(
                QueryWrapper.create().where(WORKFLOW_NODE_RUN.STATUS.eq("ERROR")));

        List<NodeAggRow> rows = fetchNodeAgg();
        long tokensIn = 0, tokensOut = 0;
        double cost = 0;
        for (NodeAggRow r : rows) {
            long ti = nz(r.getTokensIn()), to = nz(r.getTokensOut());
            tokensIn += ti;
            tokensOut += to;
            cost += ModelPricing.costCny(r.getModel(), ti, to);
        }

        PlatformOverviewVO vo = new PlatformOverviewVO();
        vo.setTotalTasks(total);
        vo.setDoneTasks(done);
        vo.setFailedTasks(failed);
        vo.setRunningTasks(running);
        vo.setTaskSuccessRate(successRate);
        vo.setTotalNodeRuns(totalNodeRuns);
        vo.setErrorNodeRuns(errorNodeRuns);
        vo.setTotalTokensIn(tokensIn);
        vo.setTotalTokensOut(tokensOut);
        vo.setTotalCostCny(round(cost, 4));
        vo.setAvgTaskLatencyMs(avgDoneTaskLatency());
        return vo;
    }

    // ── Token 成本看板 ────────────────────────────────────────

    /** 按模型聚合；跳过 model 为空的节点（如纯工具调用）。 */
    public List<ModelCostVO> costByModel() {
        Map<String, Acc> byModel = new LinkedHashMap<>();
        for (NodeAggRow r : fetchNodeAgg()) {
            if (r.getModel() == null || r.getModel().isBlank()) continue;
            byModel.computeIfAbsent(r.getModel(), k -> new Acc()).add(r);
        }
        List<ModelCostVO> out = new ArrayList<>();
        byModel.forEach((model, a) -> out.add(new ModelCostVO(
                model, a.calls, a.tokensIn, a.tokensOut, round(a.cost, 4), a.avgLatency())));
        return out;
    }

    /** 按 Agent 角色聚合；成本按各行模型分段单价累加。 */
    public List<AgentCostVO> costByAgent() {
        Map<String, Acc> byAgent = new LinkedHashMap<>();
        for (NodeAggRow r : fetchNodeAgg()) {
            String role = r.getAgentRole() == null ? "(unknown)" : r.getAgentRole();
            byAgent.computeIfAbsent(role, k -> new Acc()).add(r);
        }
        List<AgentCostVO> out = new ArrayList<>();
        byAgent.forEach((role, a) -> out.add(new AgentCostVO(
                role, a.calls, a.tokensIn, a.tokensOut, round(a.cost, 4), a.avgLatency())));
        return out;
    }

    // ── 任务列表 ──────────────────────────────────────────────

    public Page<TaskBriefVO> tasks(String status, int page, int size) {
        QueryWrapper qw = QueryWrapper.create();
        if (status != null && !status.isBlank()) {
            qw.where(REPORT_TASK.STATUS.eq(status));
        }
        qw.orderBy(REPORT_TASK.ID.desc());
        Page<ReportTask> p = taskMapper.paginate(page, size, qw);
        List<TaskBriefVO> vos = p.getRecords().stream().map(this::toBrief).toList();
        return new Page<>(vos, p.getPageNumber(), p.getPageSize(), p.getTotalRow());
    }

    // ── 内部工具 ──────────────────────────────────────────────

    private List<NodeAggRow> fetchNodeAgg() {
        return nodeRunMapper.selectListByQueryAs(
                QueryWrapper.create()
                        .select(WORKFLOW_NODE_RUN.MODEL,
                                WORKFLOW_NODE_RUN.AGENT_ROLE.as("agentRole"),
                                count().as("calls"),
                                sum(WORKFLOW_NODE_RUN.TOKENS_IN).as("tokensIn"),
                                sum(WORKFLOW_NODE_RUN.TOKENS_OUT).as("tokensOut"),
                                sum(WORKFLOW_NODE_RUN.LATENCY_MS).as("latencySum"))
                        .from(WORKFLOW_NODE_RUN)
                        .groupBy(WORKFLOW_NODE_RUN.MODEL, WORKFLOW_NODE_RUN.AGENT_ROLE),
                NodeAggRow.class);
    }

    private long countTask(String status) {
        return taskMapper.selectCountByQuery(
                QueryWrapper.create().where(REPORT_TASK.STATUS.eq(status)));
    }

    /** DONE 任务平均端到端耗时；只取 startedAt / finishedAt 两列，内存求均值。 */
    private Long avgDoneTaskLatency() {
        List<ReportTask> done = taskMapper.selectListByQuery(
                QueryWrapper.create()
                        .select(REPORT_TASK.STARTED_AT, REPORT_TASK.FINISHED_AT)
                        .where(REPORT_TASK.STATUS.eq("DONE"))
                        .and(REPORT_TASK.STARTED_AT.isNotNull())
                        .and(REPORT_TASK.FINISHED_AT.isNotNull()));
        if (done.isEmpty()) return null;
        long sum = 0;
        for (ReportTask t : done) {
            sum += Duration.between(t.getStartedAt(), t.getFinishedAt()).toMillis();
        }
        return sum / done.size();
    }

    private TaskBriefVO toBrief(ReportTask t) {
        Long latency = (t.getStartedAt() != null && t.getFinishedAt() != null)
                ? Duration.between(t.getStartedAt(), t.getFinishedAt()).toMillis()
                : null;
        Long createdAt = t.getCreateTime() == null ? null
                : t.getCreateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        return new TaskBriefVO(
                t.getId(), t.getUserId(), t.getTopic(), t.getStatus(),
                t.getPhase(), t.getProgress(), t.getErrorMessage(), latency, createdAt);
    }

    private static long nz(Long v) {
        return v == null ? 0L : v;
    }

    private static double round(double v, int scale) {
        double f = Math.pow(10, scale);
        return Math.round(v * f) / f;
    }

    /** 内存聚合累加器。 */
    private static final class Acc {
        long calls, tokensIn, tokensOut, latencySum;
        double cost;

        void add(NodeAggRow r) {
            long ti = nz(r.getTokensIn()), to = nz(r.getTokensOut());
            calls += nz(r.getCalls());
            tokensIn += ti;
            tokensOut += to;
            latencySum += nz(r.getLatencySum());
            cost += ModelPricing.costCny(r.getModel(), ti, to);
        }

        Long avgLatency() {
            return calls == 0 ? null : latencySum / calls;
        }
    }
}
