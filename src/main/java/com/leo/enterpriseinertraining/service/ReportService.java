package com.leo.enterpriseinertraining.service;

import com.leo.enterpriseinertraining.dto.ReportStartRequest;
import com.leo.enterpriseinertraining.entity.ReportTask;
import com.leo.enterpriseinertraining.vo.ReportStartVO;

public interface ReportService {

    /** 提交任务，立即返回 taskId + streamUrl，异步在 Virtual Thread 跑 workflow。 */
    ReportStartVO start(long userId, ReportStartRequest req);

    /** 查询单任务详情（DONE 后返回 markdown + citations）。 */
    ReportTask findById(long taskId);
}
