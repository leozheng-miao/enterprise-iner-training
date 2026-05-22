package com.leo.enterpriseinertraining.agent.prompt;

import com.leo.enterpriseinertraining.entity.PromptTemplate;
import com.leo.enterpriseinertraining.mapper.PromptTemplateMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static com.leo.enterpriseinertraining.entity.table.PromptTemplateTableDef.PROMPT_TEMPLATE;

/**
 * 首次启动把 {@code classpath:prompts/*.txt} 一次性导入 {@code prompt_template} 表。
 *
 * <p>文件名约定 {@code {name}_{version}.txt}（如 {@code researcher_prompt_v2.txt}），
 * 以最后一个下划线切分 name / version。导入后每个 name 的最大 version 设为 {@code is_active=1}。</p>
 *
 * <p>容错：表已有数据 → 跳过；表未建 / 不可用 → 记告警跳过（PromptLoader 仍走文件兜底）。
 * 因此本组件在执行 schema-phase4.sql 前后都不会让应用启动失败。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PromptSeeder implements ApplicationRunner {

    private final PromptTemplateMapper mapper;

    @Override
    public void run(ApplicationArguments args) {
        long count;
        try {
            count = mapper.selectCountByQuery(QueryWrapper.create());
        } catch (Exception e) {
            log.warn("[PromptSeeder] prompt_template 表不可用，跳过种子导入"
                    + "（执行 db/schema-phase4.sql 后重启即可）: {}", e.getMessage());
            return;
        }
        if (count > 0) {
            log.info("[PromptSeeder] prompt_template 已有 {} 条记录，跳过导入", count);
            return;
        }
        seedFromClasspath();
    }

    private void seedFromClasspath() {
        try {
            Resource[] files = new PathMatchingResourcePatternResolver()
                    .getResources("classpath:prompts/*.txt");
            Map<String, String> latestVersion = new HashMap<>();   // name -> 最大 version
            int imported = 0;
            for (Resource r : files) {
                String fn = r.getFilename();
                if (fn == null || !fn.endsWith(".txt")) continue;
                String base = fn.substring(0, fn.length() - 4);    // 去 ".txt"
                int us = base.lastIndexOf('_');
                if (us <= 0 || us == base.length() - 1) {
                    log.warn("[PromptSeeder] 文件名不符合 name_version.txt，跳过: {}", fn);
                    continue;
                }
                String name = base.substring(0, us);
                String version = base.substring(us + 1);
                String content = new String(r.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

                PromptTemplate t = new PromptTemplate();
                t.setName(name);
                t.setVersion(version);
                t.setContent(content);
                t.setIsActive(0);
                t.setDescription("由 classpath 文件首次导入");
                mapper.insert(t);
                imported++;
                latestVersion.merge(name, version, (a, b) -> a.compareTo(b) >= 0 ? a : b);
            }
            // 每个 name 把最大版本设为灰度生效版本
            latestVersion.forEach((name, version) -> {
                PromptTemplate active = new PromptTemplate();
                active.setIsActive(1);
                mapper.updateByQuery(active, QueryWrapper.create()
                        .where(PROMPT_TEMPLATE.NAME.eq(name))
                        .and(PROMPT_TEMPLATE.VERSION.eq(version)));
            });
            log.info("[PromptSeeder] 导入 {} 个 prompt 版本，active={}", imported, latestVersion);
        } catch (Exception e) {
            log.error("[PromptSeeder] 种子导入失败: {}", e.getMessage(), e);
        }
    }
}
