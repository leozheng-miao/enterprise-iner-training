package com.leo.enterpriseinertraining.workflow;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class WorkflowLoader {

    // ConcurrentHashMap：5 个 Consumer 线程并发 load workflow，HashMap.computeIfAbsent 非线程安全会抛 CME
    private final Map<String, WorkflowDef> cache = new ConcurrentHashMap<>();

    public WorkflowDef load(String name) {
        return cache.computeIfAbsent(name, this::readYaml);
    }

    /** 清空缓存，下次 load 会重新读 YAML（admin 接口用）。 */
    public synchronized void reload() {
        cache.clear();
        log.info("[WorkflowLoader] cache cleared (reload)");
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
