package com.leo.enterpriseinertraining.controller;

import com.leo.enterpriseinertraining.common.BaseResponse;
import com.leo.enterpriseinertraining.common.ResultUtils;
import com.leo.enterpriseinertraining.service.HealthService;
import com.leo.enterpriseinertraining.vo.ComponentHealthVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
@Tag(name = "健康检查")
public class HealthController {

    private final HealthService healthService;

    @GetMapping("")
    @Operation(summary = "存活探针")
    public BaseResponse<Map<String, Object>> health() {
        return ResultUtils.success(Map.of(
                "status", "UP",
                "time", LocalDateTime.now().toString()
        ));
    }

    @GetMapping("/components")
    @Operation(summary = "返回各子服务健康状态，供 Admin 系统健康面板使用")
    public BaseResponse<List<ComponentHealthVO>> components() {
        return ResultUtils.success(healthService.checkAll());
    }
}
