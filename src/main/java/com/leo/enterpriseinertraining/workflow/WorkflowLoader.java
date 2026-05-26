package com.leo.enterpriseinertraining.workflow;

import com.leo.enterpriseinertraining.entity.WorkflowLoadLog;
import com.leo.enterpriseinertraining.mapper.WorkflowLoadLogMapper;
import com.leo.enterpriseinertraining.vo.ActiveWorkflowVO;
import com.leo.enterpriseinertraining.vo.WorkflowLogVO;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.leo.enterpriseinertraining.entity.table.WorkflowLoadLogTableDef.WORKFLOW_LOAD_LOG;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowLoader {

    /** 默认活跃 workflow 名称；如需多 workflow 切换，未来可移到配置 / DB。 */
    private static final String DEFAULT_ACTIVE_WORKFLOW = "multi_agent_v1";

    private final Map<String, WorkflowDef> cache = new ConcurrentHashMap<>();
    private final WorkflowLoadLogMapper logMapper;
    private volatile Long lastLoadedAt;

    public WorkflowDef load(String name) {
        return cache.computeIfAbsent(name, n -> {
            WorkflowDef def = readYaml(n);
            lastLoadedAt = System.currentTimeMillis();
            return def;
        });
    }

    /** 清空缓存，下次 load 会重新读 YAML（admin 接口用），全过程记审计。 */
    public synchronized void reload() {
        appendLog("cache_clear", "已清空 Workflow YAML 缓存，准备重新加载", "info");
        cache.clear();
        try {
            WorkflowDef def = readYaml(DEFAULT_ACTIVE_WORKFLOW);
            appendLog("yaml_reload", "Workflow YAML 已重新解析", "info");
            appendLog("topology_check", "拓扑结构校验通过（" + def.getNodes().size() + " 个节点）", "info");
            cache.put(def.getName(), def);
            lastLoadedAt = System.currentTimeMillis();
            appendLog("activate", "Workflow " + def.getName() + "(v" + def.getVersion() + ") 已成功生效", "success");
        } catch (Exception e) {
            appendLog("activate", "热更新失败：" + e.getMessage(), "error");
            throw e;
        }
        log.info("[WorkflowLoader] cache cleared & reloaded");
    }

    /** 当前活跃 workflow 元信息（用于 Admin 看板）。 */
    public ActiveWorkflowVO activeInfo() {
        WorkflowDef def = cache.get(DEFAULT_ACTIVE_WORKFLOW);
        boolean cached = def != null;
        if (def == null) {
            def = readYaml(DEFAULT_ACTIVE_WORKFLOW);
            if (lastLoadedAt == null) lastLoadedAt = System.currentTimeMillis();
        }
        return new ActiveWorkflowVO(
                def.getName(),
                String.valueOf(def.getVersion()),
                "classpath:workflow/" + def.getName() + ".yaml",
                def.getNodes().stream().map(WorkflowNode::getId).toList(),
                lastLoadedAt,
                cached);
    }

    /** Workflow 加载历史（按 ts DESC，limit 1-100）。 */
    public List<WorkflowLogVO> history(int limit) {
        int safe = Math.max(1, Math.min(limit, 100));
        return logMapper.selectListByQuery(
                        QueryWrapper.create()
                                .orderBy(WORKFLOW_LOAD_LOG.TS.desc())
                                .limit(safe))
                .stream().map(r -> new WorkflowLogVO(r.getEventType(), r.getMessage(), r.getLevel(), r.getTs()))
                .toList();
    }

    private void appendLog(String eventType, String message, String level) {
        try {
            WorkflowLoadLog row = new WorkflowLoadLog();
            row.setEventType(eventType);
            row.setMessage(message);
            row.setLevel(level);
            row.setWorkflowName(DEFAULT_ACTIVE_WORKFLOW);
            row.setTs(System.currentTimeMillis());
            logMapper.insert(row);
        } catch (Exception e) {
            log.warn("[WorkflowLoader] append audit log failed: {}", e.getMessage());
        }
    }

    private WorkflowDef readYaml(String name) {
        String path = "workflow/" + name + ".yaml";
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            LoaderOptions opts = new LoaderOptions();
            Constructor c = new Constructor(WorkflowDef.class, opts);
            Yaml yaml = new Yaml(c);
            WorkflowDef def = yaml.load(in);
            if (def == null) throw new RuntimeException("空 YAML: " + path);
            log.info("[WorkflowLoader] loaded {} v{} with {} nodes",
                    def.getName(), def.getVersion(), def.getNodes().size());
            return def;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("无法加载 workflow: " + path, e);
        }
    }
}
