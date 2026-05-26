package com.leo.enterpriseinertraining.service;

import com.leo.enterpriseinertraining.stream.SseSinkManager;
import com.leo.enterpriseinertraining.vo.ComponentHealthVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class HealthService {

    private final StringRedisTemplate redisTemplate;
    private final SseSinkManager sseSinkManager;

    public List<ComponentHealthVO> checkAll() {
        List<ComponentHealthVO> rows = new ArrayList<>(3);

        rows.add(new ComponentHealthVO("API", "UP", "后端接口服务", 0.0, null));

        long t = System.nanoTime();
        try {
            String pong = redisTemplate.getConnectionFactory().getConnection().ping();
            double ms = (System.nanoTime() - t) / 1_000_000.0;
            rows.add(new ComponentHealthVO("Redis",
                    pong != null ? "UP" : "DEGRADED",
                    "缓存与会话存储",
                    Math.round(ms * 100.0) / 100.0,
                    null));
        } catch (Exception e) {
            log.warn("[HealthService] Redis ping failed: {}", e.getMessage());
            rows.add(new ComponentHealthVO("Redis", "DOWN", "缓存与会话存储", null, null));
        }

        rows.add(new ComponentHealthVO("SSE", "UP", "流式推送服务", null,
                Map.of("connections", sseSinkManager.activeCount())));

        return rows;
    }
}
