-- schema-phase8.sql  v2.0 Stage A
-- 全部可重入：ADD COLUMN IF NOT EXISTS / CREATE TABLE IF NOT EXISTS。
-- MySQL 8.0 支持 ADD COLUMN IF NOT EXISTS（8.0.29+）。如版本更低，去掉 IF NOT EXISTS 仅首次执行。

-- ① 改 prompt_template：去耦合，按 agent_role 路由
ALTER TABLE prompt_template
    ADD COLUMN IF NOT EXISTS agent_role VARCHAR(64) NULL
        COMMENT 'Planner/Researcher/Analyst/Writer/Critic/QueryRewriter/Judge' AFTER tenant_id;
CREATE INDEX idx_prompt_agent_active ON prompt_template(agent_role, is_active, tenant_id);

-- ② 新增 agent_model_routing：Agent→Model 路由（解耦核心）
CREATE TABLE IF NOT EXISTS agent_model_routing (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT NOT NULL DEFAULT 0 COMMENT '0=全局默认；>0=租户覆盖',
    agent_role      VARCHAR(64) NOT NULL,
    provider        VARCHAR(32) NOT NULL COMMENT 'OPENAI / DEEPSEEK / DASHSCOPE / VLLM',
    primary_model   VARCHAR(128) NOT NULL,
    same_provider_fallbacks   JSON NULL COMMENT '["gpt-4o-mini"]',
    cross_provider_fallbacks  JSON NULL COMMENT '[{"provider":"DASHSCOPE","model":"qwen-max"}]',
    cross_enabled   TINYINT NOT NULL DEFAULT 0,
    active          TINYINT NOT NULL DEFAULT 0,
    note            VARCHAR(255) NULL,
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted      TINYINT DEFAULT 0,
    UNIQUE KEY uk_tenant_agent_active (tenant_id, agent_role, active)
);

-- ③ 新增 routing_change_log：路由变更审计
CREATE TABLE IF NOT EXISTS routing_change_log (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id     BIGINT NOT NULL,
    agent_role    VARCHAR(64) NOT NULL,
    change_type   VARCHAR(32) NOT NULL COMMENT 'CREATE / ACTIVATE / DEACTIVATE / UPDATE',
    before_snapshot JSON NULL,
    after_snapshot  JSON NULL,
    operator_user_id BIGINT NULL,
    ts            BIGINT NOT NULL COMMENT 'epoch millis'
);
CREATE INDEX idx_routing_log_agent_ts ON routing_change_log(agent_role, ts DESC);

-- ④ 新增 alert_webhook_config（Stage A 仅建表，Entity/Mapper 留 Stage C）
CREATE TABLE IF NOT EXISTS alert_webhook_config (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id     BIGINT NOT NULL,
    webhook_url   VARCHAR(512) NOT NULL,
    webhook_type  VARCHAR(32) NOT NULL DEFAULT 'GENERIC' COMMENT 'GENERIC / FEISHU / DINGTALK / SLACK',
    secret        VARCHAR(128) NULL,
    enabled       TINYINT NOT NULL DEFAULT 1,
    min_severity  VARCHAR(16) NOT NULL DEFAULT 'CRITICAL' COMMENT 'INFO / WARN / CRITICAL 起算',
    create_time   DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tenant_webhook (tenant_id)
);

-- ⑤ 新增 alert_event（Stage A 仅建表，Entity/Mapper 留 Stage C）
CREATE TABLE IF NOT EXISTS alert_event (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id     BIGINT NOT NULL DEFAULT 0,
    severity      VARCHAR(16) NOT NULL COMMENT 'INFO / WARN / CRITICAL',
    source        VARCHAR(64) NOT NULL COMMENT 'LLM_FAILURE / CB_OPEN / CROSS_FALLBACK / ...',
    title         VARCHAR(255) NOT NULL,
    payload       JSON NULL,
    handled       TINYINT DEFAULT 0,
    ts            BIGINT NOT NULL,
    INDEX idx_alert_ts (ts DESC),
    INDEX idx_alert_unhandled (handled, severity)
);

-- ⑥ workflow_node_run 加 3 列（OTel 联动，Stage A 仅加列，写值在 Stage B TraceAdvisor）
ALTER TABLE workflow_node_run
    ADD COLUMN IF NOT EXISTS trace_id VARCHAR(64) NULL AFTER task_id,
    ADD COLUMN IF NOT EXISTS span_id  VARCHAR(32) NULL AFTER trace_id,
    ADD COLUMN IF NOT EXISTS routing_snapshot JSON NULL COMMENT 'ResolvedRoute 快照';
CREATE INDEX idx_node_run_trace ON workflow_node_run(trace_id);
