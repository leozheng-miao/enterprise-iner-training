package com.leo.enterpriseinertraining.workflow;

import com.leo.enterpriseinertraining.mq.MqMessage;
import com.leo.enterpriseinertraining.mq.MqProducerService;
import com.leo.enterpriseinertraining.mq.MqTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Workflow Engine v2（阶段 3）：事件驱动 dispatcher。
 *
 * <p>阶段 2 的同步 execute() 方法已删除。新模型：</p>
 * <ol>
 *   <li>{@link #start(long, String)} 仅发 task.created MQ 消息</li>
 *   <li>后续节点由各自 MQ Consumer 触发，节点间编排已在每个 Consumer 内显式硬编码
 *       （TaskOrchestratorConsumer → ResearcherWorker → AnalystConsumer → WriterConsumer → CriticConsumer）</li>
 * </ol>
 *
 * <p>本类目前仅做"启动入口"。未来如需通用 dispatchNext，可以扩展为读 YAML.next 决定 topic。
 * 阶段 3 保持简单：每个 Consumer 自己知道下一步去哪。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowEngine {

    private final MqProducerService producer;
    private final WorkflowLoader workflowLoader;

    /** 启动一个 task：发 task.created MQ，立即返回。 */
    public void start(long taskId, String workflowName) {
        // 校验 workflow 存在（提前失败比 MQ Consumer 失败更友好）
        WorkflowDef def = workflowLoader.load(workflowName);
        if (def.getNodes() == null || def.getNodes().isEmpty()) {
            throw new RuntimeException("workflow " + workflowName + " 无节点");
        }
        producer.send(MqTopics.TASK_CREATED, MqMessage.of(taskId, "task.created"));
        log.info("[WorkflowEngine] task {} started with workflow {}", taskId, workflowName);
    }
}
