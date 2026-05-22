package com.leo.enterpriseinertraining.trace;

import java.util.Map;

/**
 * 模型计价表：token → 成本（人民币元）。
 *
 * <p>价格为「每百万 token 单价」，取自阿里云百炼公开定价（2025）。
 * 价格会随官方调整变动，集中放此处便于统一维护；未知模型回退到 {@link #DEFAULT}。</p>
 *
 * <p>用途：{@code trace.AdminStatsService} 聚合 {@code workflow_node_run} 的
 * tokens_in / tokens_out 后，按 model 折算成本，支撑 Token 成本看板。</p>
 */
public final class ModelPricing {

    private ModelPricing() {}

    /** [输入单价, 输出单价]，单位：元 / 百万 token。 */
    private record Price(double in, double out) {}

    /** 未知模型的兜底价（按 qwen-max 量级估，避免成本被低估）。 */
    private static final Price DEFAULT = new Price(2.0, 8.0);

    private static final Map<String, Price> TABLE = Map.of(
            "qwen-max",          new Price(2.4, 9.6),
            "qwen-plus",         new Price(0.8, 2.0),
            "qwen-turbo",        new Price(0.3, 0.6),
            "text-embedding-v3", new Price(0.5, 0.0)
    );

    /**
     * 计算一次调用（或一批聚合）的成本，单位：元。
     *
     * @param model     模型名，null 视为未知模型走 {@link #DEFAULT}
     * @param tokensIn  输入 token 总数
     * @param tokensOut 输出 token 总数
     */
    public static double costCny(String model, long tokensIn, long tokensOut) {
        Price p = TABLE.getOrDefault(model == null ? "" : model, DEFAULT);
        return tokensIn / 1_000_000.0 * p.in() + tokensOut / 1_000_000.0 * p.out();
    }
}
