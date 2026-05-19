package com.leo.enterpriseinertraining.workflow;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class WorkflowLoader {

    private final Map<String, WorkflowDef> cache = new HashMap<>();

    public WorkflowDef load(String name) {
        return cache.computeIfAbsent(name, this::readYaml);
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
