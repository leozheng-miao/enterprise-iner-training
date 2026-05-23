-- 阶段 4 多租户：report_task 加 tenant_id
--
-- 隔离边界设计：
-- - 业务数据（report_task / workflow_node_run / report_section / workflow_subtask）
--   按 tenant 隔离；下游表通过 task_id 反查 report_task.tenant_id，无需冗余列。
-- - knowledge_doc / knowledge_chunk 保持全局（集中维护的行业语料库，所有租户共享）。
-- - prompt_template 保持全局（平台级 prompt 资产，所有租户共享）。
--
-- 已有数据回填：把现有 report_task 的 tenant_id 设为对应 user 的 tenant_id
-- （多数老用户 tenant_id=0，因此老任务也落在 tenant 0）。

ALTER TABLE `report_task`
    ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户 ID' AFTER `user_id`,
    ADD KEY `idx_tenant` (`tenant_id`);

UPDATE `report_task` rt
    JOIN `user` u ON u.id = rt.user_id
SET rt.tenant_id = u.tenant_id
WHERE rt.tenant_id = 0;
