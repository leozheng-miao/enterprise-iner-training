package com.leo.enterpriseinertraining.agent.prompt;

import com.leo.enterpriseinertraining.dto.PromptCreateRequest;
import com.leo.enterpriseinertraining.dto.PromptUpdateRequest;
import com.leo.enterpriseinertraining.entity.PromptTemplate;
import com.leo.enterpriseinertraining.exception.ErrorCode;
import com.leo.enterpriseinertraining.exception.ThrowUtils;
import com.leo.enterpriseinertraining.mapper.PromptTemplateMapper;
import com.leo.enterpriseinertraining.vo.PromptTemplateVO;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.List;

import static com.leo.enterpriseinertraining.entity.table.PromptTemplateTableDef.PROMPT_TEMPLATE;

/**
 * Prompt 版本管理：版本列表 / 详情 / 新建版本 / 改内容 / 切灰度生效版本。
 *
 * <p>任何写操作后都会清空 {@link PromptLoader} 缓存，使 Agent 下次调用立即取到最新
 * —— 灰度切换无需重启。</p>
 */
@Service
@RequiredArgsConstructor
public class PromptAdminService {

    private final PromptTemplateMapper mapper;
    private final PromptLoader promptLoader;

    /** 全部版本，按 name、version 升序。 */
    public List<PromptTemplateVO> list() {
        return mapper.selectListByQuery(QueryWrapper.create()
                        .orderBy(PROMPT_TEMPLATE.NAME, true)
                        .orderBy(PROMPT_TEMPLATE.VERSION, true))
                .stream().map(this::toVo).toList();
    }

    public PromptTemplateVO get(long id) {
        return toVo(require(id));
    }

    /** 新建版本：(name, version) 不可重复，新版本默认未生效。 */
    public PromptTemplateVO create(PromptCreateRequest req) {
        long dup = mapper.selectCountByQuery(QueryWrapper.create()
                .where(PROMPT_TEMPLATE.NAME.eq(req.getName()))
                .and(PROMPT_TEMPLATE.VERSION.eq(req.getVersion())));
        ThrowUtils.throwIf(dup > 0, ErrorCode.PARAMS_ERROR,
                "已存在同名同版本: " + req.getName() + "@" + req.getVersion());

        PromptTemplate t = new PromptTemplate();
        t.setName(req.getName());
        t.setVersion(req.getVersion());
        t.setContent(req.getContent());
        t.setModel(req.getModel());
        t.setTemperature(req.getTemperature());
        t.setDescription(req.getDescription());
        t.setIsActive(0);
        mapper.insert(t);
        return toVo(t);
    }

    /** 改内容 / 元信息（仅非空字段生效）；保存后清缓存。 */
    public PromptTemplateVO update(long id, PromptUpdateRequest req) {
        require(id);
        PromptTemplate upd = new PromptTemplate();
        upd.setId(id);
        upd.setContent(req.getContent());
        upd.setModel(req.getModel());
        upd.setTemperature(req.getTemperature());
        upd.setDescription(req.getDescription());
        mapper.update(upd);            // MyBatis-Flex 默认忽略 null 字段
        promptLoader.reload();
        return get(id);
    }

    /** 把指定版本设为灰度生效版本：同名其余版本全部置 0，目标置 1。 */
    @Transactional
    public PromptTemplateVO activate(long id) {
        PromptTemplate t = require(id);

        PromptTemplate off = new PromptTemplate();
        off.setIsActive(0);
        mapper.updateByQuery(off, QueryWrapper.create()
                .where(PROMPT_TEMPLATE.NAME.eq(t.getName())));

        PromptTemplate on = new PromptTemplate();
        on.setId(id);
        on.setIsActive(1);
        mapper.update(on);

        promptLoader.reload();
        return get(id);
    }

    private PromptTemplate require(long id) {
        PromptTemplate t = mapper.selectOneById(id);
        ThrowUtils.throwIf(t == null, ErrorCode.NOT_FOUND_ERROR, "prompt 版本不存在: " + id);
        return t;
    }

    private PromptTemplateVO toVo(PromptTemplate t) {
        return new PromptTemplateVO(
                t.getId(), t.getName(), t.getVersion(), t.getContent(),
                t.getModel(), t.getTemperature(),
                t.getIsActive() != null && t.getIsActive() == 1,
                t.getDescription(),
                epochMs(t.getCreateTime()), epochMs(t.getUpdateTime()));
    }

    private static Long epochMs(java.time.LocalDateTime dt) {
        return dt == null ? null
                : dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
