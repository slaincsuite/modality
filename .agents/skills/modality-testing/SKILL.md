---
name: modality-testing
description: 在 modality 写或改测试时使用，覆盖测试选层（单测/库集成/模块装配/整机验收）、H2 与真 Redis 基建、上下文隔离约定、Batch 验收模式与负向用例要求。
---

# 测试规范

> 通用测试条款（AIR 原则、BCDE）见 [java-unit-testing](../java-unit-testing/SKILL.md)；本技能是其在 modality 的落地口径。

## 选层

| 被测面 | 用什么 |
| --- | --- |
| 纯逻辑（工具、算法、状态机） | JUnit 5 单测，不起 Spring 上下文 |
| 库内集成（MyBatis/Redis/缓存/租户拦截） | 库 `src/test/java` 的 Spring 测试 + 该库测试 `application.properties` |
| 模块业务（service、事务、落库） | 模块测试，H2 + `ClasspathSchemaInitializer` 真建表 |
| 跨模块行为、端点、鉴权链路 | `modality-boot` 的 `Batch*AcceptanceTest` 整机验收 |

新能力通常两层都要：模块 / 库层测内部行为，boot 层测装配与端点。

## 基建约定

- 测试包结构镜像 main：`src/test/java` 与 main 同包
- 数据库：H2 内存库，URL 带 `MODE=MySQL`、`DB_CLOSE_DELAY=-1`；库名按模块起（如 `upmstest`）
- Redis：真连接 `127.0.0.1:6379`，密码读 `System.getenv().getOrDefault("MODALITY_REDIS_PASSWORD", "")`；**每个模块用不同的 database 编号**（security=2、upms=14，新模块挑一个没用的并写进该模块 `src/test/resources/application.properties`）——Gradle 并行跑模块，撞库会互相清数据
- 需要 Redis 的用例要能快速失败并给出中文原因（参考 `IdempotentRedisTestSupport.requireRedis()`）
- 断言：JUnit 5 + AssertJ，断言消息中文
- 上下文隔离：boot 测试由 `TestIsolationContextCustomizerFactory` 分配独立 Lucene / 本地存储目录（`src/test/resources/META-INF/spring.factories` 注册），不要共享固定目录

## 整机验收（Batch 模式）

- 命名 `Batch<N><主题>AcceptanceTest`，编号顺延；`@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@ActiveProfiles("dev")`
- HTTP 走 `AcceptanceHttpSupport`（透传 `satoken`、`X-Login-Type: TENANT_USER`）
- 测试数据：`@TestConfiguration` + `@Primary` 覆盖装配（如接真实 `t_user` 账号）；`@BeforeEach` 清理 Redis 前缀与相关表
- **正向与负向配对**：200 之外必须有 401 / 403 / 400 对照；租户、鉴权、签名、上传下载、SSRF、限流等面必须包含负向
- 时间敏感用例留足余量（历史教训：MFA 挑战过期用例放宽过时间余量）

## 纪律

- 行为变化同 PR 改测试，不许只改实现让旧测试变绿
- 不许跳过用例、降覆盖率阈值（`-PcoverageGate` 为 80% 指令覆盖率）、放宽断言语义来「通过」
- 测试失败归因流程见 [modality-troubleshooting](../modality-troubleshooting/SKILL.md)
