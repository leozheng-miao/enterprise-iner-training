package com.leo.enterpriseinertraining.codegen;

import com.mybatisflex.codegen.Generator;
import com.mybatisflex.codegen.config.GlobalConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * 一次性工具：在 IDE 里手动运行 main，按 table 名生成代码。
 * 已存在的 User 实体/Mapper 不要再覆盖。
 */
public class MybatisFlexCodegen {

    public static void main(String[] args) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:mysql://localhost:3307/irp?serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true");
        ds.setUsername("irp");
        ds.setPassword("irppw");

        GlobalConfig cfg = new GlobalConfig();
        cfg.getPackageConfig().setBasePackage("com.leo.enterpriseinertraining");
        cfg.getStrategyConfig().setGenerateTable(/* 在这里填表名，例：*/ "report_task");
        cfg.enableEntity();
        cfg.enableMapper();
        cfg.enableService();
        cfg.enableServiceImpl();
        cfg.enableController();

        new Generator(ds, cfg).generate();
    }
}
