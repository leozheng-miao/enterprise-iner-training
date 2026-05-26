package com.leo.enterpriseinertraining.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.enterpriseinertraining.entity.ReportEvalRun;
import com.leo.enterpriseinertraining.entity.ReportTask;
import com.leo.enterpriseinertraining.exception.ErrorCode;
import com.leo.enterpriseinertraining.exception.ThrowUtils;
import com.leo.enterpriseinertraining.mapper.ReportEvalRunMapper;
import com.leo.enterpriseinertraining.security.SecurityUtils;
import com.leo.enterpriseinertraining.service.ReportService;
import com.leo.enterpriseinertraining.vo.JudgeRunVO;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.leo.enterpriseinertraining.entity.table.ReportEvalRunTableDef.REPORT_EVAL_RUN;
import static com.leo.enterpriseinertraining.entity.table.ReportTaskTableDef.REPORT_TASK;

/**
 * LLM-as-Judge：用更强模型（qwen-max）对已完成研报按 5 维 rubric 打分。
 *
 * <p>访问受 tenant 隔离：{@link #judge(long)} 与 {@link #listByTask(long)} 都先走
 * {@link ReportService#findById(long)}，跨租户的 task 直接 NOT_FOUND，不会浪费 token。</p>
 *
 * <p>评分维度（rubric v1）：</p>
 * <ul>
 *   <li>structure  —— 章节结构 / 层次 / 覆盖度</li>
 *   <li>factuality —— 核心论点是否有引用支撑、数据是否准确</li>
 *   <li>reasoning  —— 分析逻辑、有无矛盾</li>
 *   <li>citation   —— 引用相关性 / 来源质量</li>
 *   <li>clarity    —— 语言流畅、专业易读</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportJudgeService {

    static final String RUBRIC_VERSION = "v1";

    static final String JUDGE_SYSTEM_PROMPT = """
            你是研报质量评审专家。从 5 个维度给报告打分（每项 0-10，可保留 1 位小数）：
            - structure  : 章节结构是否完整、层次清晰、覆盖核心议题
            - factuality : 核心论点是否有引用支撑、数据是否准确
            - reasoning  : 分析逻辑是否严谨、有无矛盾
            - citation   : 引用是否相关、来源是否可靠
            - clarity    : 语言是否流畅、专业但易读

            严格只输出下面这种 JSON，不要 markdown 围栏，不要额外文字：
            {
              "structure":  <0-10>,
              "factuality": <0-10>,
              "reasoning":  <0-10>,
              "citation":   <0-10>,
              "clarity":    <0-10>,
              "comments": {
                "structure":  "...",
                "factuality": "...",
                "reasoning":  "...",
                "citation":   "...",
                "clarity":    "..."
              }
            }
            """;

    /** 报告正文截断长度，控制 judge 调用 token 成本上限。 */
    private static final int MD_MAX_CHARS = 6000;

    private final ReportService reportService;
    private final ReportEvalRunMapper evalRunMapper;
    private final com.leo.enterpriseinertraining.mapper.ReportTaskMapper reportTaskMapper;
    private final ObjectMapper om;

    @Value("${spring.ai.openai.api-key:}")
    private String dashScopeApiKey;

    @Value("${spring.ai.openai.base-url:https://dashscope.aliyuncs.com/compatible-mode}")
    private String dashScopeBaseUrl;

    @Value("${app.dashscope.judge-model:qwen-max}")
    private String judgeModel;

    @Value("#{'${app.dashscope.judge-model-candidates:qwen-max}'.split(',')}")
    private List<String> judgeModelCandidates;

    private RestClient http;

    @PostConstruct
    void init() {
        // spring.ai.openai.base-url 不含 /v1，OpenAI 兼容端点是 /v1/chat/completions
        String url = dashScopeBaseUrl.endsWith("/v1") ? dashScopeBaseUrl : dashScopeBaseUrl + "/v1";
        this.http = RestClient.builder()
                .baseUrl(url)
                .defaultHeader("Authorization", "Bearer " + (dashScopeApiKey == null ? "" : dashScopeApiKey))
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
        log.info("[ReportJudgeService] judge endpoint={} model={}", url, judgeModel);
    }

    /** 兼容旧调用方：默认不强制重评分，使用默认 judgeModel。 */
    public JudgeRunVO judge(long taskId) {
        return judge(taskId, null, false);
    }

    public JudgeRunVO judge(long taskId, String judgeModelOverride, boolean force) {
        ReportTask task = reportService.findById(taskId);    // tenant 校验
        String model = resolveModel(judgeModelOverride);
        String rubric = RUBRIC_VERSION;

        if (!force) {
            List<ReportEvalRun> hits = evalRunMapper.selectListByQuery(
                    QueryWrapper.create()
                            .where(REPORT_EVAL_RUN.TASK_ID.eq(taskId))
                            .and(REPORT_EVAL_RUN.JUDGE_MODEL.eq(model))
                            .and(REPORT_EVAL_RUN.RUBRIC_VERSION.eq(rubric))
                            .orderBy(REPORT_EVAL_RUN.ID.desc())
                            .limit(1));
            if (!hits.isEmpty()) {
                JudgeRunVO vo = toVo(hits.get(0));
                vo.setTopic(task.getTopic());
                return vo;
            }
        }

        ThrowUtils.throwIf(task.getFinalMarkdown() == null || task.getFinalMarkdown().isBlank(),
                ErrorCode.PARAMS_ERROR, "task " + taskId + " 尚无 finalMarkdown，无法评分");

        String md = task.getFinalMarkdown();
        if (md.length() > MD_MAX_CHARS) md = md.substring(0, MD_MAX_CHARS) + "\n...（已截断）";
        String citations = task.getCitationsJson() == null ? "[]" : safeWriteJson(task.getCitationsJson());
        String userMsg = """
                【研报主题】
                %s

                【研报正文】
                %s

                【引用列表 JSON】
                %s
                """.formatted(task.getTopic(), md, citations);

        long t0 = System.currentTimeMillis();
        String content = callJudge(userMsg, model);
        int latency = (int) (System.currentTimeMillis() - t0);

        JsonNode root = parseJson(content);
        ReportEvalRun run = new ReportEvalRun();
        run.setTaskId(taskId);
        run.setJudgeModel(model);
        run.setRubricVersion(rubric);
        run.setScoreStructure(asDouble(root, "structure"));
        run.setScoreFactuality(asDouble(root, "factuality"));
        run.setScoreReasoning(asDouble(root, "reasoning"));
        run.setScoreCitation(asDouble(root, "citation"));
        run.setScoreClarity(asDouble(root, "clarity"));
        run.setScoreOverall(avg(run.getScoreStructure(), run.getScoreFactuality(),
                run.getScoreReasoning(), run.getScoreCitation(), run.getScoreClarity()));
        run.setBreakdownJson(root.has("comments") ? root.get("comments").toString() : null);
        run.setLatencyMs(latency);
        evalRunMapper.insert(run);

        log.info("[ReportJudgeService] task {} judged with {}: overall={} latency={}ms",
                taskId, model, run.getScoreOverall(), latency);
        JudgeRunVO vo = toVo(run);
        vo.setTopic(task.getTopic());
        return vo;
    }

    private String resolveModel(String override) {
        return (override != null && !override.isBlank()) ? override : this.judgeModel;
    }

    public List<String> listJudgeModels() {
        if (judgeModelCandidates == null || judgeModelCandidates.isEmpty()) {
            return List.of(judgeModel);
        }
        return judgeModelCandidates.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public List<JudgeRunVO> listByTask(long taskId) {
        ReportTask task = reportService.findById(taskId);    // tenant 校验 + 取 topic
        return evalRunMapper.selectListByQuery(QueryWrapper.create()
                        .where(REPORT_EVAL_RUN.TASK_ID.eq(taskId))
                        .orderBy(REPORT_EVAL_RUN.ID.desc()))
                .stream().map(r -> {
                    JudgeRunVO vo = toVo(r);
                    vo.setTopic(task.getTopic());
                    return vo;
                }).toList();
    }

    /** 当前租户全部历史评分，跨租户的 task 通过子查询屏蔽。 */
    public List<JudgeRunVO> listForCurrentTenant(int limit) {
        QueryWrapper sub = QueryWrapper.create()
                .select(REPORT_TASK.ID)
                .from(REPORT_TASK)
                .where(REPORT_TASK.TENANT_ID.eq(SecurityUtils.currentTenantId()));
        List<ReportEvalRun> runs = evalRunMapper.selectListByQuery(QueryWrapper.create()
                .where(REPORT_EVAL_RUN.TASK_ID.in(sub))
                .orderBy(REPORT_EVAL_RUN.ID.desc())
                .limit(Math.max(1, Math.min(limit, 200))));
        if (runs.isEmpty()) return List.of();

        java.util.Set<Long> taskIds = runs.stream().map(ReportEvalRun::getTaskId)
                .collect(java.util.stream.Collectors.toSet());
        Map<Long, String> topicByTaskId = reportTaskMapper.selectListByQuery(
                        QueryWrapper.create()
                                .select(REPORT_TASK.ID, REPORT_TASK.TOPIC)
                                .where(REPORT_TASK.ID.in(taskIds))
                                .and(REPORT_TASK.TENANT_ID.eq(SecurityUtils.currentTenantId())))
                .stream()
                .collect(java.util.stream.Collectors.toMap(ReportTask::getId, ReportTask::getTopic));

        return runs.stream().map(r -> {
            JudgeRunVO vo = toVo(r);
            vo.setTopic(topicByTaskId.get(r.getTaskId()));
            return vo;
        }).toList();
    }

    // ── 内部 ───────────────────────────────────────────────

    @SuppressWarnings({"unchecked", "rawtypes"})
    private String callJudge(String userMsg, String model) {
        Map<String, Object> body = Map.of(
                "model", model,
                "temperature", 0.0,
                "messages", List.of(
                        Map.of("role", "system", "content", JUDGE_SYSTEM_PROMPT),
                        Map.of("role", "user", "content", userMsg)));
        try {
            Map resp = http.post().uri("/chat/completions").body(body).retrieve().body(Map.class);
            List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.get("choices");
            Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
            return stripFences((String) msg.get("content"));
        } catch (Exception e) {
            throw new RuntimeException("judge LLM 调用失败: " + e.getMessage(), e);
        }
    }

    private JsonNode parseJson(String s) {
        try {
            return om.readTree(s);
        } catch (Exception e) {
            throw new RuntimeException("judge 输出不是合法 JSON: " + s, e);
        }
    }

    private String safeWriteJson(Object o) {
        try {
            return om.writeValueAsString(o);
        } catch (Exception e) {
            return "[]";
        }
    }

    private static String stripFences(String s) {
        s = s.trim();
        if (s.startsWith("```")) {
            int nl = s.indexOf('\n');
            if (nl > 0) s = s.substring(nl + 1);
            if (s.endsWith("```")) s = s.substring(0, s.length() - 3);
            s = s.trim();
        }
        return s;
    }

    private static Double asDouble(JsonNode root, String field) {
        JsonNode n = root.get(field);
        return n == null || n.isNull() ? null : Math.round(n.asDouble() * 10.0) / 10.0;
    }

    private static Double avg(Double... vs) {
        double s = 0;
        int c = 0;
        for (Double v : vs) {
            if (v != null) { s += v; c++; }
        }
        return c == 0 ? null : Math.round(s / c * 10.0) / 10.0;
    }

    private JudgeRunVO toVo(ReportEvalRun r) {
        Map<String, String> comments = parseComments(r.getBreakdownJson());
        JudgeRunVO vo = new JudgeRunVO();
        vo.setId(r.getId());
        vo.setTaskId(r.getTaskId());
        vo.setJudgeModel(r.getJudgeModel());
        vo.setRubricVersion(r.getRubricVersion());
        vo.setOverall(r.getScoreOverall());
        vo.setStructure(r.getScoreStructure());
        vo.setFactuality(r.getScoreFactuality());
        vo.setReasoning(r.getScoreReasoning());
        vo.setCitation(r.getScoreCitation());
        vo.setClarity(r.getScoreClarity());
        vo.setComments(comments);
        vo.setLatencyMs(r.getLatencyMs());
        vo.setCreateTime(r.getCreateTime() == null ? null
                : r.getCreateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        // topic 由调用方按需填充（listByTask 单任务批注一次；recent 走批量 JOIN）
        return vo;
    }

    private Map<String, String> parseComments(String json) {
        if (json == null || json.isBlank()) return new HashMap<>();
        try {
            JsonNode n = om.readTree(json);
            Map<String, String> m = new LinkedHashMap<>();
            n.fieldNames().forEachRemaining(k -> m.put(k, n.get(k).asText("")));
            return m;
        } catch (Exception e) {
            return new HashMap<>();
        }
    }
}
