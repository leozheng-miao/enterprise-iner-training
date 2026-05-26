package com.leo.enterpriseinertraining.mapper;

import com.leo.enterpriseinertraining.entity.ReportTask;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

public interface ReportTaskMapper extends BaseMapper<ReportTask> {

    /**
     * 窗口内已完成（DONE、startedAt/finishedAt 都非空）的任务数；P95 采样基数。
     */
    @Select("""
            SELECT COUNT(*) FROM report_task
            WHERE tenant_id = #{tenantId}
              AND status = 'DONE'
              AND started_at IS NOT NULL
              AND finished_at IS NOT NULL
              AND create_time >= #{from}
              AND create_time < #{to}
              AND is_deleted = 0
            """)
    long countDoneTasksInWindow(@Param("tenantId") Long tenantId,
                                @Param("from") LocalDateTime from,
                                @Param("to") LocalDateTime to);

    /**
     * 窗口内 DONE 任务按耗时升序的第 offset+1 条耗时（毫秒）。OFFSET = floor(count * 0.95)。
     */
    @Select("""
            SELECT TIMESTAMPDIFF(MICROSECOND, started_at, finished_at) / 1000 AS latency_ms
            FROM report_task
            WHERE tenant_id = #{tenantId}
              AND status = 'DONE'
              AND started_at IS NOT NULL
              AND finished_at IS NOT NULL
              AND create_time >= #{from}
              AND create_time < #{to}
              AND is_deleted = 0
            ORDER BY latency_ms
            LIMIT 1 OFFSET #{offset}
            """)
    Long selectP95LatencyMs(@Param("tenantId") Long tenantId,
                            @Param("from") LocalDateTime from,
                            @Param("to") LocalDateTime to,
                            @Param("offset") long offset);

    /**
     * 窗口内租户总任务数（用于同比聚合）。
     */
    @Select("""
            SELECT COUNT(*) FROM report_task
            WHERE tenant_id = #{tenantId}
              AND create_time >= #{from}
              AND create_time < #{to}
              AND is_deleted = 0
            """)
    long countTasksInWindow(@Param("tenantId") Long tenantId,
                            @Param("from") LocalDateTime from,
                            @Param("to") LocalDateTime to);

    /**
     * 窗口内租户 DONE 任务数（用于同比成功率）。
     */
    @Select("""
            SELECT COUNT(*) FROM report_task
            WHERE tenant_id = #{tenantId}
              AND status = 'DONE'
              AND create_time >= #{from}
              AND create_time < #{to}
              AND is_deleted = 0
            """)
    long countDoneInWindow(@Param("tenantId") Long tenantId,
                           @Param("from") LocalDateTime from,
                           @Param("to") LocalDateTime to);

    /**
     * 窗口内租户 DONE 任务的平均耗时 ms。无样本返回 null。
     */
    @Select("""
            SELECT AVG(TIMESTAMPDIFF(MICROSECOND, started_at, finished_at) / 1000) AS avg_ms
            FROM report_task
            WHERE tenant_id = #{tenantId}
              AND status = 'DONE'
              AND started_at IS NOT NULL
              AND finished_at IS NOT NULL
              AND create_time >= #{from}
              AND create_time < #{to}
              AND is_deleted = 0
            """)
    Long avgLatencyInWindow(@Param("tenantId") Long tenantId,
                            @Param("from") LocalDateTime from,
                            @Param("to") LocalDateTime to);

    /**
     * 窗口内租户 workflow_node_run 累计 tokens_in 与 tokens_out。
     */
    @Select("""
            SELECT COALESCE(SUM(wnr.tokens_in), 0)  AS tokens_in,
                   COALESCE(SUM(wnr.tokens_out), 0) AS tokens_out
            FROM report_task t
            LEFT JOIN workflow_node_run wnr ON wnr.task_id = t.id
            WHERE t.tenant_id = #{tenantId}
              AND t.create_time >= #{from}
              AND t.create_time < #{to}
              AND t.is_deleted = 0
            """)
    java.util.Map<String, Object> sumTokensInWindow(@Param("tenantId") Long tenantId,
                                                    @Param("from") LocalDateTime from,
                                                    @Param("to") LocalDateTime to);
}
