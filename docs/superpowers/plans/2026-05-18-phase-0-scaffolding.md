# 阶段 0：环境与脚手架 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal：** 在现有空白 Spring Boot 项目上搭建可运行的开发脚手架 —— 完成依赖、配置、基础设施编排、SpringSecurity + JWT 鉴权、MyBatis-Flex 集成、注册登录接口与 Knife4j，能跑通 `注册 → 登录 → 携带 JWT 访问受保护接口` 端到端冒烟测试。

**Architecture：** 单模块 Spring Boot 3.5 + Java 21 + Virtual Thread。基础设施通过 docker-compose 一键拉起（MySQL/Redis/RocketMQ/PGVector/ES）。鉴权采用无状态 JWT + SpringSecurity 6 的新式 Lambda 配置 DSL。所有响应统一通过 `BaseResponse` + `ResultUtils` 封装。异常统一交给已有 `GlobalExceptionHandler`。

**Tech Stack：** Spring Boot 3.5.14 / Java 21 / Spring Security 6 / MyBatis-Flex 1.10.x / MySQL 8 / Redis 7 / RocketMQ 5 / jjwt 0.12.x / Knife4j 4.5 / Hutool / Lombok / PGVector & ES 客户端（仅引入，阶段 1 才用）。

**项目根：** `/Users/zhengsmacbook/Desktop/miniProject/claude/enterprise-iner-training`

---

## 文件结构（阶段 0 完成后）

```
enterprise-iner-training/
├── docker-compose.yml                                   ← 新增
├── pom.xml                                              ← 修改
├── src/main/resources/
│   ├── application.yml                                  ← 新增（替代 application.properties）
│   ├── application-dev.yml                              ← 新增
│   └── db/
│       └── schema.sql                                   ← 新增
├── src/main/java/com/leo/enterpriseinertraining/
│   ├── EnterpriseInerTrainingApplication.java          ← 修改（加 MapperScan）
│   ├── common/                                          ← 沿用，无改动
│   ├── exception/
│   │   ├── ErrorCode.java                              ← 修改（补充错误码）
│   │   └── GlobalExceptionHandler.java                 ← 修改（增加 Security/参数校验）
│   ├── config/
│   │   ├── MybatisFlexConfig.java                      ← 新增
│   │   ├── Knife4jConfig.java                          ← 新增
│   │   └── RedisConfig.java                            ← 新增
│   ├── security/
│   │   ├── JwtProperties.java                          ← 新增
│   │   ├── JwtUtils.java                               ← 新增（TDD）
│   │   ├── JwtAuthenticationFilter.java                ← 新增
│   │   ├── LoginUser.java                              ← 新增
│   │   ├── UserDetailsServiceImpl.java                 ← 新增
│   │   ├── SecurityConfig.java                         ← 新增
│   │   └── SecurityUtils.java                          ← 新增
│   ├── user/
│   │   ├── entity/User.java                            ← 新增
│   │   ├── mapper/UserMapper.java                      ← 新增
│   │   ├── dto/UserRegisterRequest.java                ← 新增
│   │   ├── dto/UserLoginRequest.java                   ← 新增
│   │   ├── vo/LoginVO.java                             ← 新增
│   │   ├── vo/UserVO.java                              ← 新增
│   │   ├── service/UserService.java                    ← 新增
│   │   ├── service/impl/UserServiceImpl.java           ← 新增
│   │   └── controller/UserController.java              ← 新增
│   └── health/
│       └── HealthController.java                       ← 新增
├── src/test/java/com/leo/enterpriseinertraining/
│   ├── EnterpriseInerTrainingApplicationTests.java     ← 沿用
│   ├── security/JwtUtilsTest.java                      ← 新增
│   └── codegen/MybatisFlexCodegen.java                 ← 新增（一次性工具）
└── docs/superpowers/plans/                              ← 本计划所在
```

---

## Task 1：pom.xml 增加阶段 0 必需依赖

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1.1：替换 `pom.xml` 全部内容为下方版本**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.14</version>
        <relativePath/>
    </parent>
    <groupId>com.leo</groupId>
    <artifactId>enterprise-iner-training</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>enterprise-iner-training</name>
    <description>行业研报多 Agent 协作平台</description>
    <properties>
        <java.version>21</java.version>
        <mybatis-flex.version>1.10.6</mybatis-flex.version>
        <jjwt.version>0.12.6</jjwt.version>
        <rocketmq.version>2.3.3</rocketmq.version>
        <pgvector.version>0.1.6</pgvector.version>
        <hutool.version>5.8.43</hutool.version>
        <knife4j.version>4.5.0</knife4j.version>
        <spring-ai.version>1.0.0</spring-ai.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.ai</groupId>
                <artifactId>spring-ai-bom</artifactId>
                <version>${spring-ai.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <!-- Spring Boot 基础 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-aop</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>

        <!-- MyBatis-Flex -->
        <dependency>
            <groupId>com.mybatis-flex</groupId>
            <artifactId>mybatis-flex-spring-boot3-starter</artifactId>
            <version>${mybatis-flex.version}</version>
        </dependency>
        <dependency>
            <groupId>com.mybatis-flex</groupId>
            <artifactId>mybatis-flex-codegen</artifactId>
            <version>${mybatis-flex.version}</version>
            <scope>test</scope>
        </dependency>

        <!-- MySQL -->
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- Postgres + PGVector（仅引入，阶段 1 才接入数据源） -->
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>com.pgvector</groupId>
            <artifactId>pgvector</artifactId>
            <version>${pgvector.version}</version>
        </dependency>

        <!-- RocketMQ -->
        <dependency>
            <groupId>org.apache.rocketmq</groupId>
            <artifactId>rocketmq-spring-boot-starter</artifactId>
            <version>${rocketmq.version}</version>
        </dependency>

        <!-- JWT -->
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>${jjwt.version}</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>

        <!-- Spring AI（阶段 2 才用，1.0.0 GA 命名为 spring-ai-starter-model-openai） -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-starter-model-openai</artifactId>
        </dependency>

        <!-- 工具 -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
        </dependency>
        <dependency>
            <groupId>cn.hutool</groupId>
            <artifactId>hutool-all</artifactId>
            <version>${hutool.version}</version>
        </dependency>
        <dependency>
            <groupId>com.github.xiaoymin</groupId>
            <artifactId>knife4j-openapi3-jakarta-spring-boot-starter</artifactId>
            <version>${knife4j.version}</version>
        </dependency>

        <!-- 测试 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 1.2：跑 `./mvnw dependency:resolve` 验证依赖下载成功**

Run：`./mvnw dependency:resolve -q`
Expected：无报错，退出码 0。

- [ ] **Step 1.3：跑编译验证**

Run：`./mvnw clean compile -q`
Expected：`BUILD SUCCESS`。

- [ ] **Step 1.4：提交**

```bash
git add pom.xml
git commit -m "feat(deps): add phase-0 dependencies (mybatis-flex/security/redis/rocketmq/jwt/spring-ai-bom)"
```

---

## Task 2：docker-compose.yml 本地基础设施

**Files:**
- Create: `docker-compose.yml`

- [ ] **Step 2.1：创建 docker-compose.yml**

```yaml
services:
  mysql:
    image: mysql:8.0
    container_name: irp-mysql
    restart: unless-stopped
    environment:
      MYSQL_ROOT_PASSWORD: rootpw
      MYSQL_DATABASE: irp
      MYSQL_USER: irp
      MYSQL_PASSWORD: irppw
      TZ: Asia/Shanghai
    ports:
      - "3307:3306"          # 宿主 3307（本地已占用 3306）
    command:
      - --character-set-server=utf8mb4
      - --collation-server=utf8mb4_unicode_ci
    volumes:
      - irp-mysql-data:/var/lib/mysql

  redis:
    image: redis:7-alpine
    container_name: irp-redis
    restart: unless-stopped
    ports:
      - "6380:6379"          # 宿主 6380（本地已有 redis-server 在 6379）
    command: ["redis-server", "--appendonly", "yes"]
    volumes:
      - irp-redis-data:/data

  rocketmq-namesrv:
    image: apache/rocketmq:5.3.0
    container_name: irp-rmq-namesrv
    restart: unless-stopped
    ports:
      - "9877:9876"          # 宿主 9877（9876 被其它容器占用）
    command: sh mqnamesrv

  rocketmq-broker:
    image: apache/rocketmq:5.3.0
    container_name: irp-rmq-broker
    restart: unless-stopped
    depends_on:
      - rocketmq-namesrv
    ports:
      - "10911:10911"
      - "10909:10909"
    environment:
      - NAMESRV_ADDR=rocketmq-namesrv:9876   # 容器内通信，不动
    command: sh mqbroker -c /home/rocketmq/rocketmq-5.3.0/conf/broker.conf
    volumes:
      - ./docker/rocketmq/broker.conf:/home/rocketmq/rocketmq-5.3.0/conf/broker.conf

  pgvector:
    image: pgvector/pgvector:pg16
    container_name: irp-pgvector
    restart: unless-stopped
    environment:
      POSTGRES_USER: irp
      POSTGRES_PASSWORD: irppw
      POSTGRES_DB: irp_vec
    ports:
      - "5434:5432"          # 宿主 5434（5432 + 5433 都被宿主进程占用）
    volumes:
      - irp-pg-data:/var/lib/postgresql/data

  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.13.0
    container_name: irp-es
    restart: unless-stopped
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
      - ES_JAVA_OPTS=-Xms512m -Xmx512m
    ports:
      - "9201:9200"          # 宿主 9201（9200 被其它容器占用）
    volumes:
      - irp-es-data:/usr/share/elasticsearch/data

volumes:
  irp-mysql-data:
  irp-redis-data:
  irp-pg-data:
  irp-es-data:
```

- [ ] **Step 2.2：创建 broker 配置**

Run：`mkdir -p docker/rocketmq`

Create：`docker/rocketmq/broker.conf`
```conf
brokerClusterName = DefaultCluster
brokerName = broker-a
brokerId = 0
deleteWhen = 04
fileReservedTime = 48
brokerRole = ASYNC_MASTER
flushDiskType = ASYNC_FLUSH
brokerIP1 = 127.0.0.1
autoCreateTopicEnable = true
```

- [ ] **Step 2.3：启动并验证**

Run：`docker compose up -d`
然后：`docker compose ps`
Expected：6 个容器全部 `Up` 状态。

逐个连通性验证（注意：从容器内部访问用容器内端口，从宿主访问用映射后端口）：
```bash
docker exec irp-mysql mysql -uirp -pirppw -e "SELECT 1;"
docker exec irp-redis redis-cli ping              # 应输出 PONG
curl -s http://localhost:9201                     # 走宿主映射端口，应返回 ES 集群 JSON
docker exec irp-pgvector psql -U irp -d irp_vec -c "SELECT 1;"
docker exec irp-rmq-namesrv sh -c "ls /home/rocketmq"
```

- [ ] **Step 2.4：提交**

```bash
git add docker-compose.yml docker/
git commit -m "feat(infra): docker-compose for mysql/redis/rocketmq/pgvector/es"
```

> **阶段 3 验证项：** `broker.conf` 中 `brokerIP1=127.0.0.1` 适用于"客户端跑宿主、broker 端口未偏移"的场景（本项目即此场景）。阶段 3 真正启用 RocketMQ 生产消费时，确认 producer/consumer 能正常拿到 broker 地址；若失败，改为 `brokerIP1=host.docker.internal` 或暴露容器实际 IP。

---

## Task 3：application.yml 配置文件迁移

**Files:**
- Delete: `src/main/resources/application.properties`
- Create: `src/main/resources/application.yml`
- Create: `src/main/resources/application-dev.yml`

- [ ] **Step 3.1：删除 application.properties**

Run：`rm src/main/resources/application.properties`

- [ ] **Step 3.2：创建 `src/main/resources/application.yml`**

```yaml
spring:
  application:
    name: enterprise-iner-training
  profiles:
    active: dev
  threads:
    virtual:
      enabled: true
  sql:
    init:
      mode: never

server:
  port: 8080
  servlet:
    context-path: /
  tomcat:
    threads:
      max: 200

# Knife4j 文档
springdoc:
  swagger-ui:
    path: /swagger-ui.html
  api-docs:
    path: /v3/api-docs
  group-configs:
    - group: default
      paths-to-match: '/**'
      packages-to-scan: com.leo.enterpriseinertraining

knife4j:
  enable: true
  setting:
    language: zh_cn

# MyBatis-Flex
mybatis-flex:
  global-config:
    print-banner: false
    logic-delete-column: is_deleted
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl

logging:
  level:
    root: INFO
    com.leo.enterpriseinertraining: DEBUG
    org.springframework.security: INFO

# 自定义 JWT 配置（被 JwtProperties 绑定）
app:
  jwt:
    secret: "change-me-please-use-at-least-32-bytes-secret-key-for-hs256"
    expire-millis: 86400000          # 24h
    header: Authorization
    prefix: "Bearer "
```

- [ ] **Step 3.3：创建 `src/main/resources/application-dev.yml`**

```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3307/irp?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&characterEncoding=utf8
    username: irp
    password: irppw

  data:
    redis:
      host: localhost
      port: 6380
      timeout: 3s
      lettuce:
        pool:
          max-active: 16
          max-idle: 8
          min-idle: 1

rocketmq:
  name-server: 127.0.0.1:9877
  producer:
    group: irp-producer-group
    send-message-timeout: 5000
```

- [ ] **Step 3.4：本步暂不启动应用（依赖未实现完）**

仅静态校验：`./mvnw -q resources:resources`
Expected：BUILD SUCCESS。

- [ ] **Step 3.5：提交**

```bash
git add src/main/resources/
git commit -m "feat(config): migrate to yml + add dev profile (datasource/redis/rocketmq/jwt)"
```

---

## Task 4：user 表 DDL

**Files:**
- Create: `src/main/resources/db/schema.sql`

- [ ] **Step 4.1：创建 schema.sql**

```sql
-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`      VARCHAR(64)  NOT NULL COMMENT '用户名',
    `password_hash` VARCHAR(255) NOT NULL COMMENT 'BCrypt 密码',
    `nickname`      VARCHAR(64)  DEFAULT NULL COMMENT '昵称',
    `tenant_id`     BIGINT       NOT NULL DEFAULT 0 COMMENT '租户 ID',
    `role`          VARCHAR(32)  NOT NULL DEFAULT 'USER' COMMENT '角色：USER / ADMIN',
    `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '1=启用 0=禁用',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`    TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';
```

- [ ] **Step 4.2：手动执行**

Run（从宿主使用映射端口；或者用 docker exec 进容器执行）：
```bash
# 选项 A：从宿主连
mysql -h 127.0.0.1 -P 3307 -uirp -pirppw irp < src/main/resources/db/schema.sql
# 选项 B：通过 docker exec
docker exec -i irp-mysql mysql -uirp -pirppw irp < src/main/resources/db/schema.sql
```

验证：
```bash
docker exec irp-mysql mysql -uirp -pirppw irp -e "SHOW TABLES; DESC user;"
```
Expected：能看到 `user` 表及完整字段。

- [ ] **Step 4.3：提交**

```bash
git add src/main/resources/db/schema.sql
git commit -m "feat(db): user table DDL"
```

---

## Task 5：扩展 ErrorCode 与 GlobalExceptionHandler

**Files:**
- Modify: `src/main/java/com/leo/enterpriseinertraining/exception/ErrorCode.java`
- Modify: `src/main/java/com/leo/enterpriseinertraining/exception/GlobalExceptionHandler.java`

- [ ] **Step 5.1：在 ErrorCode 枚举末尾追加新错误码**

修改 `ErrorCode.java`，在 `OPERATION_ERROR(50001, "操作失败");` 之前补充：

```java
    SUCCESS(0, "ok"),
    PARAMS_ERROR(40000, "请求参数错误"),
    NOT_LOGIN_ERROR(40100, "未登录"),
    NO_AUTH_ERROR(40101, "无权限"),
    TOO_MANY_REQUEST(42900, "请求过于频繁"),
    NOT_FOUND_ERROR(40400, "请求数据不存在"),
    FORBIDDEN_ERROR(40300, "禁止访问"),
    USER_EXIST(40010, "用户名已存在"),
    USER_NOT_EXIST(40011, "用户不存在"),
    PASSWORD_ERROR(40012, "用户名或密码错误"),
    JWT_INVALID(40110, "JWT 无效或已过期"),
    SYSTEM_ERROR(50000, "系统内部异常"),
    OPERATION_ERROR(50001, "操作失败");
```

- [ ] **Step 5.2：在 GlobalExceptionHandler 中追加参数校验与 Security 异常处理**

替换文件全部内容为：

```java
package com.leo.enterpriseinertraining.exception;

import com.leo.enterpriseinertraining.common.BaseResponse;
import com.leo.enterpriseinertraining.common.ResultUtils;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
@Hidden
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> businessExceptionHandler(BusinessException e) {
        log.warn("BusinessException: {}", e.getMessage());
        return ResultUtils.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public BaseResponse<?> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .orElse("参数校验失败");
        return ResultUtils.error(ErrorCode.PARAMS_ERROR.getCode(), msg);
    }

    @ExceptionHandler(BindException.class)
    public BaseResponse<?> handleBind(BindException e) {
        return ResultUtils.error(ErrorCode.PARAMS_ERROR.getCode(), "参数绑定失败");
    }

    @ExceptionHandler(AuthenticationException.class)
    public BaseResponse<?> handleAuth(AuthenticationException e) {
        log.warn("AuthenticationException: {}", e.getMessage());
        return ResultUtils.error(ErrorCode.NOT_LOGIN_ERROR);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public BaseResponse<?> handleAccessDenied(AccessDeniedException e) {
        return ResultUtils.error(ErrorCode.NO_AUTH_ERROR);
    }

    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> runtimeExceptionHandler(RuntimeException e) {
        log.error("RuntimeException", e);
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
    }
}
```

- [ ] **Step 5.3：编译验证**

Run：`./mvnw -q compile`
Expected：BUILD SUCCESS。

- [ ] **Step 5.4：提交**

```bash
git add src/main/java/com/leo/enterpriseinertraining/exception/
git commit -m "feat(error): add user/jwt error codes and security/validation exception handling"
```

---

## Task 6：User 实体 + Mapper（MyBatis-Flex）

**Files:**
- Create: `src/main/java/com/leo/enterpriseinertraining/user/entity/User.java`
- Create: `src/main/java/com/leo/enterpriseinertraining/user/mapper/UserMapper.java`
- Modify: `src/main/java/com/leo/enterpriseinertraining/EnterpriseInerTrainingApplication.java`

- [ ] **Step 6.1：创建 `User.java`**

```java
package com.leo.enterpriseinertraining.user.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Table("user")
public class User implements Serializable {

    @Id(keyType = KeyType.Auto)
    private Long id;
    private String username;
    private String passwordHash;
    private String nickname;
    private Long tenantId;
    private String role;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @Column(isLogicDelete = true)
    private Integer isDeleted;
}
```

- [ ] **Step 6.2：创建 `UserMapper.java`**

```java
package com.leo.enterpriseinertraining.user.mapper;

import com.mybatisflex.core.BaseMapper;
import com.leo.enterpriseinertraining.user.entity.User;

public interface UserMapper extends BaseMapper<User> {
}
```

- [ ] **Step 6.3：在主类加 `@MapperScan`**

替换 `EnterpriseInerTrainingApplication.java` 全部内容为：

```java
package com.leo.enterpriseinertraining;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.leo.enterpriseinertraining.**.mapper")
public class EnterpriseInerTrainingApplication {

    public static void main(String[] args) {
        SpringApplication.run(EnterpriseInerTrainingApplication.class, args);
    }
}
```

- [ ] **Step 6.4：编译**

Run：`./mvnw -q compile`
Expected：BUILD SUCCESS。

- [ ] **Step 6.5：提交**

```bash
git add src/main/java/com/leo/enterpriseinertraining/user/ src/main/java/com/leo/enterpriseinertraining/EnterpriseInerTrainingApplication.java
git commit -m "feat(user): User entity + UserMapper + global MapperScan"
```

---

## Task 7：JwtProperties + JwtUtils（TDD）

**Files:**
- Create: `src/main/java/com/leo/enterpriseinertraining/security/JwtProperties.java`
- Test: `src/test/java/com/leo/enterpriseinertraining/security/JwtUtilsTest.java`
- Create: `src/main/java/com/leo/enterpriseinertraining/security/JwtUtils.java`

- [ ] **Step 7.1：创建 `JwtProperties.java`**

```java
package com.leo.enterpriseinertraining.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {
    private String secret;
    private long expireMillis;
    private String header = "Authorization";
    private String prefix = "Bearer ";
}
```

- [ ] **Step 7.2：先写失败的测试 `JwtUtilsTest.java`**

```java
package com.leo.enterpriseinertraining.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilsTest {

    private JwtUtils jwtUtils;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret("test-secret-for-hs256-must-be-at-least-32-bytes-please");
        props.setExpireMillis(60_000L);
        jwtUtils = new JwtUtils(props);
    }

    @Test
    void generate_and_parse_round_trip() {
        String token = jwtUtils.generate(42L, "alice", "USER");
        assertNotNull(token);

        JwtUtils.JwtPayload p = jwtUtils.parse(token);
        assertEquals(42L, p.userId());
        assertEquals("alice", p.username());
        assertEquals("USER", p.role());
    }

    @Test
    void parse_invalid_token_throws() {
        assertThrows(RuntimeException.class, () -> jwtUtils.parse("not-a-jwt"));
    }

    @Test
    void parse_expired_token_throws() {
        JwtProperties props = new JwtProperties();
        props.setSecret("test-secret-for-hs256-must-be-at-least-32-bytes-please");
        props.setExpireMillis(1L);
        JwtUtils shortLived = new JwtUtils(props);
        String token = shortLived.generate(1L, "u", "USER");
        try { Thread.sleep(20); } catch (InterruptedException ignored) {}
        assertThrows(RuntimeException.class, () -> shortLived.parse(token));
    }
}
```

- [ ] **Step 7.3：跑测试确认失败**

Run：`./mvnw -q -Dtest=JwtUtilsTest test`
Expected：编译失败（`JwtUtils` 还不存在）。

- [ ] **Step 7.4：实现 `JwtUtils.java`**

```java
package com.leo.enterpriseinertraining.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtils {

    private final JwtProperties props;
    private final SecretKey key;

    public JwtUtils(JwtProperties props) {
        this.props = props;
        this.key = Keys.hmacShaKeyFor(props.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generate(Long userId, String username, String role) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .issuedAt(new Date(now))
                .expiration(new Date(now + props.getExpireMillis()))
                .signWith(key)
                .compact();
    }

    public JwtPayload parse(String token) {
        Claims c = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return new JwtPayload(
                Long.parseLong(c.getSubject()),
                c.get("username", String.class),
                c.get("role", String.class)
        );
    }

    public record JwtPayload(Long userId, String username, String role) {}
}
```

- [ ] **Step 7.5：再次跑测试确认通过**

Run：`./mvnw -q -Dtest=JwtUtilsTest test`
Expected：`Tests run: 3, Failures: 0, Errors: 0`。

- [ ] **Step 7.6：提交**

```bash
git add src/main/java/com/leo/enterpriseinertraining/security/JwtProperties.java src/main/java/com/leo/enterpriseinertraining/security/JwtUtils.java src/test/java/com/leo/enterpriseinertraining/security/JwtUtilsTest.java
git commit -m "feat(security): JwtProperties + JwtUtils with TDD"
```

---

## Task 8：LoginUser + UserDetailsServiceImpl

**Files:**
- Create: `src/main/java/com/leo/enterpriseinertraining/security/LoginUser.java`
- Create: `src/main/java/com/leo/enterpriseinertraining/security/UserDetailsServiceImpl.java`

- [ ] **Step 8.1：创建 `LoginUser.java`**

```java
package com.leo.enterpriseinertraining.security;

import com.leo.enterpriseinertraining.user.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
public class LoginUser implements UserDetails {

    private final User user;

    public LoginUser(User user) {
        this.user = user;
    }

    public Long getId() { return user.getId(); }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()));
    }
    @Override public String getPassword() { return user.getPasswordHash(); }
    @Override public String getUsername() { return user.getUsername(); }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return user.getStatus() == 1; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return user.getStatus() == 1; }
}
```

- [ ] **Step 8.2：创建 `UserDetailsServiceImpl.java`**

```java
package com.leo.enterpriseinertraining.security;

import com.leo.enterpriseinertraining.user.entity.User;
import com.leo.enterpriseinertraining.user.mapper.UserMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import static com.leo.enterpriseinertraining.user.entity.table.UserTableDef.USER;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String username) {
        User user = userMapper.selectOneByQuery(
                QueryWrapper.create().where(USER.USERNAME.eq(username))
        );
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在");
        }
        return new LoginUser(user);
    }
}
```

注：`UserTableDef` 是 MyBatis-Flex APT 在编译阶段自动生成的元数据类，编译后即可使用；若 IDE 报红，先执行 Step 8.3 编译。

- [ ] **Step 8.3：编译触发 APT 生成**

Run：`./mvnw -q compile`
Expected：BUILD SUCCESS。

- [ ] **Step 8.4：提交**

```bash
git add src/main/java/com/leo/enterpriseinertraining/security/LoginUser.java src/main/java/com/leo/enterpriseinertraining/security/UserDetailsServiceImpl.java
git commit -m "feat(security): LoginUser + UserDetailsServiceImpl"
```

---

## Task 9：JwtAuthenticationFilter

**Files:**
- Create: `src/main/java/com/leo/enterpriseinertraining/security/JwtAuthenticationFilter.java`

- [ ] **Step 9.1：创建过滤器**

```java
package com.leo.enterpriseinertraining.security;

import com.leo.enterpriseinertraining.user.entity.User;
import com.leo.enterpriseinertraining.user.mapper.UserMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProperties props;
    private final JwtUtils jwtUtils;
    private final UserMapper userMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(props.getHeader());
        if (header != null && header.startsWith(props.getPrefix())) {
            String token = header.substring(props.getPrefix().length());
            try {
                JwtUtils.JwtPayload payload = jwtUtils.parse(token);
                User user = userMapper.selectOneById(payload.userId());
                if (user != null && user.getStatus() == 1) {
                    LoginUser loginUser = new LoginUser(user);
                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(
                                    loginUser, null, loginUser.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (Exception e) {
                log.debug("JWT 解析失败: {}", e.getMessage());
            }
        }
        chain.doFilter(request, response);
    }
}
```

- [ ] **Step 9.2：编译**

Run：`./mvnw -q compile`
Expected：BUILD SUCCESS。

- [ ] **Step 9.3：提交**

```bash
git add src/main/java/com/leo/enterpriseinertraining/security/JwtAuthenticationFilter.java
git commit -m "feat(security): JwtAuthenticationFilter parses Bearer token and sets context"
```

---

## Task 10：SecurityConfig + SecurityUtils

**Files:**
- Create: `src/main/java/com/leo/enterpriseinertraining/security/SecurityConfig.java`
- Create: `src/main/java/com/leo/enterpriseinertraining/security/SecurityUtils.java`

- [ ] **Step 10.1：创建 `SecurityConfig.java`**

```java
package com.leo.enterpriseinertraining.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;

    private static final String[] WHITELIST = {
            "/api/user/register",
            "/api/user/login",
            "/api/health/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/doc.html",
            "/webjars/**",
            "/favicon.ico"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(reg -> reg
                        .requestMatchers(WHITELIST).permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }
}
```

- [ ] **Step 10.2：创建 `SecurityUtils.java`**

```java
package com.leo.enterpriseinertraining.security;

import com.leo.enterpriseinertraining.exception.BusinessException;
import com.leo.enterpriseinertraining.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static LoginUser currentUserOrThrow() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginUser lu)) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return lu;
    }

    public static Long currentUserId() {
        return currentUserOrThrow().getId();
    }
}
```

- [ ] **Step 10.3：编译**

Run：`./mvnw -q compile`
Expected：BUILD SUCCESS。

- [ ] **Step 10.4：提交**

```bash
git add src/main/java/com/leo/enterpriseinertraining/security/SecurityConfig.java src/main/java/com/leo/enterpriseinertraining/security/SecurityUtils.java
git commit -m "feat(security): stateless SecurityConfig with JWT filter + SecurityUtils"
```

---

## Task 11：用户注册接口

**Files:**
- Create: `src/main/java/com/leo/enterpriseinertraining/user/dto/UserRegisterRequest.java`
- Create: `src/main/java/com/leo/enterpriseinertraining/user/vo/UserVO.java`
- Create: `src/main/java/com/leo/enterpriseinertraining/user/service/UserService.java`
- Create: `src/main/java/com/leo/enterpriseinertraining/user/service/impl/UserServiceImpl.java`
- Create: `src/main/java/com/leo/enterpriseinertraining/user/controller/UserController.java`

- [ ] **Step 11.1：DTO `UserRegisterRequest.java`**

```java
package com.leo.enterpriseinertraining.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
public class UserRegisterRequest implements Serializable {

    @NotBlank(message = "不能为空")
    @Size(min = 4, max = 32, message = "长度必须在 4-32")
    private String username;

    @NotBlank(message = "不能为空")
    @Size(min = 6, max = 64, message = "长度必须在 6-64")
    private String password;

    @Size(max = 64, message = "长度不能超过 64")
    private String nickname;
}
```

- [ ] **Step 11.2：VO `UserVO.java`**

```java
package com.leo.enterpriseinertraining.user.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class UserVO implements Serializable {
    private Long id;
    private String username;
    private String nickname;
    private String role;
}
```

- [ ] **Step 11.3：`UserService.java`**

```java
package com.leo.enterpriseinertraining.user.service;

import com.leo.enterpriseinertraining.user.dto.UserLoginRequest;
import com.leo.enterpriseinertraining.user.dto.UserRegisterRequest;
import com.leo.enterpriseinertraining.user.vo.LoginVO;
import com.leo.enterpriseinertraining.user.vo.UserVO;

public interface UserService {
    UserVO register(UserRegisterRequest req);
    LoginVO login(UserLoginRequest req);
}
```

注：`UserLoginRequest` 与 `LoginVO` 在 Task 12 创建；本步只编译接口签名（IDE 会有红线，等 Task 12 完成后消失）。**为避免红线，把 Task 11 与 Task 12 一起完整完成再编译**。

- [ ] **Step 11.4：先暂跳到 Task 12 完成 DTO 与 VO，然后回到 Step 11.5**

跳到 Task 12 的 Step 12.1、12.2 完成 `UserLoginRequest` 与 `LoginVO`，然后回到这里。

- [ ] **Step 11.5：`UserServiceImpl.java`（注册逻辑）**

```java
package com.leo.enterpriseinertraining.user.service.impl;

import com.leo.enterpriseinertraining.exception.BusinessException;
import com.leo.enterpriseinertraining.exception.ErrorCode;
import com.leo.enterpriseinertraining.exception.ThrowUtils;
import com.leo.enterpriseinertraining.security.JwtUtils;
import com.leo.enterpriseinertraining.user.dto.UserLoginRequest;
import com.leo.enterpriseinertraining.user.dto.UserRegisterRequest;
import com.leo.enterpriseinertraining.user.entity.User;
import com.leo.enterpriseinertraining.user.mapper.UserMapper;
import com.leo.enterpriseinertraining.user.service.UserService;
import com.leo.enterpriseinertraining.user.vo.LoginVO;
import com.leo.enterpriseinertraining.user.vo.UserVO;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.leo.enterpriseinertraining.user.entity.table.UserTableDef.USER;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    @Override
    @Transactional
    public UserVO register(UserRegisterRequest req) {
        long existing = userMapper.selectCountByQuery(
                QueryWrapper.create().where(USER.USERNAME.eq(req.getUsername())));
        ThrowUtils.throwIf(existing > 0, ErrorCode.USER_EXIST);

        User user = new User();
        user.setUsername(req.getUsername());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setNickname(req.getNickname() == null ? req.getUsername() : req.getNickname());
        user.setTenantId(0L);
        user.setRole("USER");
        user.setStatus(1);
        userMapper.insert(user);

        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }

    @Override
    public LoginVO login(UserLoginRequest req) {
        User user = userMapper.selectOneByQuery(
                QueryWrapper.create().where(USER.USERNAME.eq(req.getUsername())));
        if (user == null) {
            throw new BusinessException(ErrorCode.PASSWORD_ERROR);
        }
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.PASSWORD_ERROR);
        }
        ThrowUtils.throwIf(user.getStatus() != 1, ErrorCode.FORBIDDEN_ERROR);

        String token = jwtUtils.generate(user.getId(), user.getUsername(), user.getRole());

        UserVO uv = new UserVO();
        BeanUtils.copyProperties(user, uv);
        return new LoginVO(token, uv);
    }
}
```

- [ ] **Step 11.6：`UserController.java`**

```java
package com.leo.enterpriseinertraining.user.controller;

import com.leo.enterpriseinertraining.common.BaseResponse;
import com.leo.enterpriseinertraining.common.ResultUtils;
import com.leo.enterpriseinertraining.security.LoginUser;
import com.leo.enterpriseinertraining.security.SecurityUtils;
import com.leo.enterpriseinertraining.user.dto.UserLoginRequest;
import com.leo.enterpriseinertraining.user.dto.UserRegisterRequest;
import com.leo.enterpriseinertraining.user.service.UserService;
import com.leo.enterpriseinertraining.user.vo.LoginVO;
import com.leo.enterpriseinertraining.user.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Tag(name = "用户", description = "注册 / 登录 / 当前用户")
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    @Operation(summary = "注册")
    public BaseResponse<UserVO> register(@RequestBody @Valid UserRegisterRequest req) {
        return ResultUtils.success(userService.register(req));
    }

    @PostMapping("/login")
    @Operation(summary = "登录")
    public BaseResponse<LoginVO> login(@RequestBody @Valid UserLoginRequest req) {
        return ResultUtils.success(userService.login(req));
    }

    @GetMapping("/me")
    @Operation(summary = "当前用户（需 JWT）")
    public BaseResponse<UserVO> me() {
        LoginUser lu = SecurityUtils.currentUserOrThrow();
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(lu.getUser(), vo);
        return ResultUtils.success(vo);
    }
}
```

- [ ] **Step 11.7：编译**

Run：`./mvnw -q compile`
Expected：BUILD SUCCESS（前提：Task 12 的 DTO/VO 已建）。

- [ ] **Step 11.8：提交**

```bash
git add src/main/java/com/leo/enterpriseinertraining/user/
git commit -m "feat(user): register/login/me API + UserService impl with BCrypt + JWT"
```

---

## Task 12：登录 DTO/VO

**Files:**
- Create: `src/main/java/com/leo/enterpriseinertraining/user/dto/UserLoginRequest.java`
- Create: `src/main/java/com/leo/enterpriseinertraining/user/vo/LoginVO.java`

- [ ] **Step 12.1：`UserLoginRequest.java`**

```java
package com.leo.enterpriseinertraining.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

@Data
public class UserLoginRequest implements Serializable {
    @NotBlank(message = "不能为空")
    private String username;

    @NotBlank(message = "不能为空")
    private String password;
}
```

- [ ] **Step 12.2：`LoginVO.java`**

```java
package com.leo.enterpriseinertraining.user.vo;

import com.leo.enterpriseinertraining.user.vo.UserVO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginVO implements Serializable {
    private String token;
    private UserVO user;
}
```

（注：与 Task 11 合并提交即可，无需单独 commit）

---

## Task 13：健康检查 + Knife4j 验证 + 启动

**Files:**
- Create: `src/main/java/com/leo/enterpriseinertraining/health/HealthController.java`
- Create: `src/main/java/com/leo/enterpriseinertraining/config/Knife4jConfig.java`

- [ ] **Step 13.1：`HealthController.java`**

```java
package com.leo.enterpriseinertraining.health;

import com.leo.enterpriseinertraining.common.BaseResponse;
import com.leo.enterpriseinertraining.common.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
@Tag(name = "健康检查")
public class HealthController {

    @GetMapping("")
    @Operation(summary = "存活探针")
    public BaseResponse<Map<String, Object>> health() {
        return ResultUtils.success(Map.of(
                "status", "UP",
                "time", LocalDateTime.now().toString()
        ));
    }
}
```

- [ ] **Step 13.2：`Knife4jConfig.java`**

```java
package com.leo.enterpriseinertraining.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("行业研报多 Agent 协作平台 API")
                        .description("Inflow Research Platform")
                        .version("v0.1"))
                .components(new Components().addSecuritySchemes("BearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("BearerAuth"));
    }
}
```

- [ ] **Step 13.3：完整编译**

Run：`./mvnw -q clean compile test-compile`
Expected：BUILD SUCCESS。

- [ ] **Step 13.4：提交**

```bash
git add src/main/java/com/leo/enterpriseinertraining/health/ src/main/java/com/leo/enterpriseinertraining/config/Knife4jConfig.java
git commit -m "feat: health probe + Knife4j OpenAPI with Bearer auth"
```

---

## Task 14：MyBatis-Flex 代码生成器（一次性工具）

**Files:**
- Create: `src/test/java/com/leo/enterpriseinertraining/codegen/MybatisFlexCodegen.java`

- [ ] **Step 14.1：创建生成器**

```java
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
        ds.setJdbcUrl("jdbc:mysql://localhost:3306/irp?serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true");
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
```

- [ ] **Step 14.2：编译验证（不运行 main）**

Run：`./mvnw -q test-compile`
Expected：BUILD SUCCESS。

- [ ] **Step 14.3：提交**

```bash
git add src/test/java/com/leo/enterpriseinertraining/codegen/
git commit -m "chore(codegen): MyBatis-Flex generator scaffold for later phases"
```

---

## Task 15：端到端冒烟测试

**Files:** 无新增；本任务是验证 + 一次最终提交。

- [ ] **Step 15.1：确保基础设施在运行**

Run：`docker compose ps`
Expected：6 个容器均 Up；MySQL `user` 表存在。
若 `user` 表不存在：`docker exec -i irp-mysql mysql -uirp -pirppw irp < src/main/resources/db/schema.sql`

- [ ] **Step 15.2：启动应用**

Run（前台启动便于看日志）：`./mvnw spring-boot:run`
Expected：终端出现 `Started EnterpriseInerTrainingApplication in X seconds`，无 ERROR 堆栈。

- [ ] **Step 15.3：另开终端跑冒烟用例**

```bash
# 1. 健康检查
curl -s http://localhost:8080/api/health

# 2. 注册
curl -s -X POST http://localhost:8080/api/user/register \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"alice123","nickname":"Alice"}'

# 3. 登录拿 token
TOKEN=$(curl -s -X POST http://localhost:8080/api/user/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"alice123"}' | sed -E 's/.*"token":"([^"]+)".*/\1/')
echo "TOKEN=$TOKEN"

# 4. 无 token 访问受保护接口（应 401/40100）
curl -s http://localhost:8080/api/user/me

# 5. 携带 token 访问 /me（应返回用户信息）
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/user/me

# 6. 错误密码登录
curl -s -X POST http://localhost:8080/api/user/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"wrong"}'

# 7. 参数校验（密码太短）
curl -s -X POST http://localhost:8080/api/user/register \
  -H "Content-Type: application/json" \
  -d '{"username":"bob","password":"123"}'
```

**预期：**
- (1) `{"code":0,"data":{"status":"UP",...},"message":"ok"}`
- (2) `{"code":0,"data":{"id":1,"username":"alice",...},"message":"ok"}`
- (3) token 非空字符串
- (4) `{"code":40100,"data":null,"message":"未登录"}`
- (5) `{"code":0,"data":{"id":1,"username":"alice",...},"message":"ok"}`
- (6) `{"code":40012,"data":null,"message":"用户名或密码错误"}`
- (7) `{"code":40000,"data":null,"message":"password 长度必须在 6-64"}`

- [ ] **Step 15.4：Knife4j UI 验证**

打开浏览器：`http://localhost:8080/doc.html`
Expected：能看到 "用户" 和 "健康检查" 两个 Tag，所有接口列表正常；右上角能填 Bearer Token 后调试 `/me`。

- [ ] **Step 15.5：跑全部测试**

停止应用（Ctrl+C），然后：
Run：`./mvnw test`
Expected：`Tests run: 4+, Failures: 0, Errors: 0` —— `JwtUtilsTest` 3 个 + `EnterpriseInerTrainingApplicationTests` 上下文加载 1 个。

注：`EnterpriseInerTrainingApplicationTests` 需要能连上 MySQL/Redis；若你没起 docker 而要跑这条，先 docker 起来。

- [ ] **Step 15.6：清理与最终提交**

如有冒烟过程中写的临时文件，回滚。然后：
```bash
git status
git add -A   # 若仍有遗漏的格式化或换行调整
git commit -m "chore: phase-0 end-to-end smoke test passed" --allow-empty
```

- [ ] **Step 15.7：勾选验证清单（写回 §7 的 plan）**

打开 `~/.claude/plans/now-in-china-sorted-treasure.md`，将以下"验证方式"项打勾：
- [x] 用户注册登录 + JWT 鉴权通过

---

## 阶段 0 出口条件（DoD）

- ✅ `./mvnw clean test` 全绿
- ✅ `docker compose up -d` 全部基础设施 Up
- ✅ `./mvnw spring-boot:run` 启动无 ERROR
- ✅ 冒烟用例 7 条全部预期返回
- ✅ Knife4j `/doc.html` 可访问，能填 Bearer 调试 `/me`
- ✅ git log 至少 10 个语义化 commit

---

## Self-Review（writing-plans 自检结果）

**1. Spec 覆盖：**
- 设计 §1.2 基础设施层 → Task 1 + Task 2 全部覆盖（MySQL/Redis/RocketMQ/PGVector/ES）✓
- 设计 §5 SpringSecurity（JWT + 鉴权）→ Task 7-10 全部覆盖 ✓
- 设计 §2.1 user 表 → Task 4 + Task 6 ✓
- 设计「沿用 BaseResponse/ErrorCode/GlobalExceptionHandler/ThrowUtils」→ Task 5 显式扩展、其余沿用 ✓
- 设计 §7 阶段 0 交付物「能注册登录 + 数据库连接 + Swagger 可用」→ Task 15 端到端冒烟覆盖 ✓
- 未在阶段 0 涉及的项（向量库、ES 数据源、Spring AI 启用、RocketMQ 实际生产消费）—— 故意延后到阶段 1+，符合 YAGNI ✓

**2. Placeholder 扫描：** 无 TBD / TODO / 未填代码块；所有 step 都有可执行命令或代码 ✓

**3. 类型一致性：**
- `JwtUtils.JwtPayload(userId, username, role)` 在 Task 7 定义 → Task 9 `JwtAuthenticationFilter` 使用 `payload.userId()` 一致 ✓
- `LoginVO(String token, UserVO user)` 在 Task 12 定义 → Task 11.5 `UserServiceImpl.login` `new LoginVO(token, uv)` 一致 ✓
- `UserMapper extends BaseMapper<User>` → Task 8 `selectOneByQuery`、Task 11 `selectCountByQuery / insert` 全部来自 BaseMapper API ✓
- `UserTableDef.USER` 来自 MyBatis-Flex APT 编译生成，Task 8 / Task 11 使用前都已 `./mvnw compile` ✓
- `SecurityUtils.currentUserOrThrow()` 返回 `LoginUser`（含 `getUser()` `getId()`）→ Task 11.6 `UserController.me` 使用一致 ✓

无遗漏。

---

## 执行交付选项

阶段 0 计划完成，已保存至 `docs/superpowers/plans/2026-05-18-phase-0-scaffolding.md`。两种执行方式：

**1. Subagent-Driven（推荐）** —— 我每个 Task 派一个新 subagent 执行 + 两阶段 review，进度可控、回归风险低。

**2. Inline Execution** —— 在当前会话直接执行所有 Task，期间设置 checkpoint 暂停 review。

请选择 1 或 2。
