package com.leo.enterpriseinertraining.agent.prompt;

import com.leo.enterpriseinertraining.entity.PromptTemplate;
import com.leo.enterpriseinertraining.mapper.PromptTemplateMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.leo.enterpriseinertraining.entity.table.PromptTemplateTableDef.PROMPT_TEMPLATE;

/**
 * 加载 prompt 文本，按 {@code "name@version"} 索引并缓存。
 *
 * <p>调用约定：<br>
 * - {@code load("researcher_prompt@v2")} → 精确版本<br>
 * - {@code load("researcher_prompt@active")} → 当前灰度生效版本。</p>
 *
 * <p>阶段 4 起改为 <b>DB 优先</b>：先查 {@code prompt_template} 表，命中即返回；
 * 未命中（表为空 / 未建 / 该版本不存在）回退到 {@code classpath:prompts/} 文件。
 * 文件兜底同样支持 {@code @active} —— 取 {@code {name}_*.txt} 中版本号最大的文件，
 * 与 PromptSeeder「最大版本设为 active」的规则一致。因此执行 schema-phase4.sql
 * 前后、以及 workflow 用 {@code @active} 还是精确版本，都不会中断运行。</p>
 *
 * <p>缓存：admin 改 prompt 或切灰度版本后会调用 {@link #reload()} 清空，使下次 load 取最新。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PromptLoader {

    private final PromptTemplateMapper mapper;

    // ConcurrentHashMap：多个 Consumer 线程并发 load 同一 prompt，HashMap.computeIfAbsent 非线程安全会抛 CME
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String load(String ref) {
        return cache.computeIfAbsent(ref, this::resolve);
    }

    /** 清空缓存：admin 改 prompt / 切灰度版本后调用，下次 load 重新解析。 */
    public void reload() {
        cache.clear();
        log.info("[PromptLoader] cache cleared (reload)");
    }

    private String resolve(String ref) {
        int at = ref.indexOf('@');
        if (at < 0) throw new IllegalArgumentException("prompt ref 必须形如 name@version: " + ref);
        String name = ref.substring(0, at);
        String version = ref.substring(at + 1);

        String fromDb = loadFromDb(name, version);
        if (fromDb != null) return fromDb;

        return loadFromFile(name, version, ref);
    }

    /** DB 优先：version=active 取灰度生效版本，否则按精确版本号查；表不可用时返回 null 走文件兜底。 */
    private String loadFromDb(String name, String version) {
        try {
            QueryWrapper qw = QueryWrapper.create().where(PROMPT_TEMPLATE.NAME.eq(name));
            if (isActive(version)) {
                qw.and(PROMPT_TEMPLATE.IS_ACTIVE.eq(1));
            } else {
                qw.and(PROMPT_TEMPLATE.VERSION.eq(version));
            }
            PromptTemplate t = mapper.selectOneByQuery(qw);
            if (t != null) {
                log.info("[PromptLoader] loaded {}@{} from DB ({} chars)",
                        name, t.getVersion(), t.getContent().length());
                return t.getContent();
            }
        } catch (Exception e) {
            log.warn("[PromptLoader] DB 查询失败，回退 classpath 文件: {}", e.getMessage());
        }
        return null;
    }

    /** classpath 兜底：prompt_template 表为空 / 未建时仍可用。 */
    private String loadFromFile(String name, String version, String ref) {
        if (isActive(version)) {
            return loadActiveFromFile(name, ref);
        }
        String path = "prompts/" + name + "_" + version + ".txt";
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            String s = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            log.info("[PromptLoader] loaded {} from classpath ({} chars)", ref, s.length());
            return s;
        } catch (Exception e) {
            throw new RuntimeException("无法加载 prompt: " + ref + "（DB 与 classpath 均无）", e);
        }
    }

    /** @active 的文件兜底：取 classpath:prompts/{name}_*.txt 中版本号最大的文件。 */
    private String loadActiveFromFile(String name, String ref) {
        try {
            Resource[] files = new PathMatchingResourcePatternResolver()
                    .getResources("classpath:prompts/" + name + "_*.txt");
            Resource latest = null;
            String latestVer = null;
            for (Resource r : files) {
                String fn = r.getFilename();
                if (fn == null || !fn.endsWith(".txt")) continue;
                String base = fn.substring(0, fn.length() - 4);
                String ver = base.substring(base.lastIndexOf('_') + 1);
                if (latestVer == null || ver.compareTo(latestVer) > 0) {
                    latestVer = ver;
                    latest = r;
                }
            }
            if (latest == null) {
                throw new RuntimeException("无法加载 prompt: " + ref
                        + "（DB 无 active 版本，classpath 也无 " + name + "_*.txt）");
            }
            String s = new String(latest.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            log.info("[PromptLoader] loaded {} -> {}_{} from classpath ({} chars)",
                    ref, name, latestVer, s.length());
            return s;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("无法加载 prompt: " + ref, e);
        }
    }

    private static boolean isActive(String version) {
        return "active".equalsIgnoreCase(version);
    }
}
