# AGENTS.md

modality 是企业多租户 Spring Boot 基础平台（GPL-3.0）。本文件与 `.agents/` 目录已纳入版本管理（2026-10-16 起），改动随常规分支流程提交，全仓库可见——**不要写入任何凭据与个人信息**；面向人类的文档见 `README.md`。

四个约定源：**规则**看本文件，**开发规范**看 `.agents/skills/java-*`（7 个标准技能），**工作流**看 `.agents/skills/modality-*`，**决策依据**看 `.agents/notes/`。动手改 `modality-library/`、`modality-modules/`、`modality-services/` 前先读完「硬性约定」。

## 仓库布局

```
modality-library/    34 个库（33 个能力库 + BOM）：只提供能力与服务，不注册端点，可被单独引入
modality-modules/    9 个业务模块：端点与落库（upms/auth/open/audit/policy/infra/workorder/tenant/job）
modality-services/   业务服务：与业务模块同层、只能依赖库（live-monitor）
modality-boot/       启动模块：只做聚合装配与配置，不含业务端点
db/                  DDL：各模块 *-schema.sql，启动经 ClasspathSchemaInitializer 幂等执行
buildSrc/            Gradle 约定插件（java / spring-library / spring-boot-application）
```

技术栈：JDK 21、Spring Boot 4.1.1 / Spring 7 / Jackson 3、Sa-Token 1.46.0、MyBatis-Plus、Redisson、Lucene、Javers；Gradle 9.8.0（wrapper 锁定），group `cn.slainc`。

## 常用命令

| 目的 | 命令 |
| --- | --- |
| 全量构建 + 全部测试 | `./gradlew clean build --no-build-cache` |
| 单模块测试 | `./gradlew :modality-library:modality-library-spring-boot-tenant:test` |
| 单测试类 | `./gradlew :modality-boot:test --tests 'cn.slainc.boot.Batch15WorkOrderAcceptanceTest'` |
| 整机验收（全部 Batch） | `./gradlew :modality-boot:test` |
| 架构规则 | `./gradlew :modality-boot:test --tests 'cn.slainc.boot.ArchitectureTest'` |
| 本地启动 | `./gradlew :modality-boot:bootRun`（默认 dev profile：H2 内存库 + `127.0.0.1:6379` Redis） |
| 打可执行 jar | `./gradlew :modality-boot:bootJar` |
| 覆盖率门槛 | `./gradlew build -PcoverageGate`（各模块指令覆盖率 ≥ 80%） |

环境前提：

- 跑测试 / 启动前必须 `export MODALITY_REDIS_PASSWORD=<本机 Redis 密码>`；Redis 必须开密码，否则 NOAUTH 直接失败；真连接用例连 `127.0.0.1:6379`
- `dev` profile 仅供本地调试；H2 是 `developmentOnly` 依赖，不在 fat jar 里，打包产物必须提供真实 MySQL
- 依赖锁文件（`gradle.lockfile`、`settings-gradle.lockfile`）不入库，新增依赖无需提交锁文件

提交前选验证、判失败，用 [modality-pre-push-checks](.agents/skills/modality-pre-push-checks/SKILL.md)；本仓库没有 CI 兜底，推送 / 宣布完成前跑通全量构建是硬性要求。

## 硬性约定

每条自包含；括注是详细工作流所在：

- **分层不可逆**：`utils`/`crypto` → `core` → 能力库 → 业务模块 / 业务服务 → `boot`；库不依赖业务模块，业务模块之间、业务服务之间禁止互相依赖；跨模块能力一律下沉为「接口 + 模型」SPI
- **库与模块包名互斥**：库内禁止 `service`/`web`/`dto`/`entity`/`mapper`/`model`/`impl`/`vo`/`form`/`controller`/`repository`/`api`/`admin` 包名（`cn.slainc.web` 库本体豁免）；实现类放 `support`/`loader`/`dao`/`domain`/`spi` 等技术词包（ArchUnit 为准，细则见 [modality-layout](.agents/skills/modality-layout/SKILL.md)）
- **端点只在业务模块 `web` 包**：库内不得出现 `@RestController`/`@Controller`/`@RequestMapping`（`@RestControllerAdvice` 与 Actuator `@Endpoint` 例外）；控制器按域进 `web/<域>/` 子包
- **自动配置完备**：类放 `autoconfigure` 包并登记 `AutoConfiguration.imports`；每个 Bean `@ConditionalOnMissingBean`；能力可 `@ConditionalOnProperty(..., matchIfMissing = true)` 关闭；`compileOnly` 坐标必须配 `@ConditionalOnClass(name = "...")`
- **无构造型注解**：不用 `@Service`/`@Component`/`@Repository`，Bean 一律在 `autoconfigure` 显式 `@Bean` 声明；构造器注入 + `private final`（[modality-code-design](.agents/skills/modality-code-design/SKILL.md)）
- **包名与模块对应**：`api-key` → `cn.slainc.apikey`，`open-api` → `cn.slainc.openapi`；业务模块 `cn.slainc.modules.<x>`，业务服务 `cn.slainc.services.<x>`
- **注释极少**：Java 只允许 `//` 单行注释；只在反直觉常量、绕坑、顺序敏感处写一行；配置文件零注释
- **文案全中文**：异常消息、日志、断言提示、枚举描述全中文；算法名、MIME、协议头、字符集保留原文
- **类名不带项目前缀**，直接表达能力（`TenantAutoConfiguration`）
- **授权术语口径**：权限骨架称 RBAC（用户 / 角色 / 权限码 / 数据范围），细粒度判定称「策略文档型 ABAC」（策略语义对齐 AWS IAM Policy / 腾讯云 CAM 策略，自研实现）；文档、注释与提交信息统一按此口径，不写成「接入云 CAM/IAM」
- **依赖**：版本只在 `gradle/libs.versions.toml` 维护，模块里禁止版本字面量；能 `implementation` 就不 `api`
- **新表 fail-closed**：平台级表必须登记租户忽略集（`TenantIgnoreTables.merge`，模式见 `UpmsTableWhitelist`）；租户业务表必须带 `tenant_id`；漏登记要么直接报错、要么越权，没有中间态（[modality-new-table](.agents/skills/modality-new-table/SKILL.md)）
- **安全默认收紧**：CORS 空白名单、Cookie `Secure`、异常不回显原始 message、弱算法默认禁用；dev 放宽只许留在 dev 配置
- **DDL 幂等**：`db/*-schema.sql` 全部 `CREATE TABLE IF NOT EXISTS`，同时兼容 H2（`MODE=MySQL`）与 MySQL 8
- **提交**：单行 `type(scope): 中文一句话`，实现与测试分开提交；从 `main` 切 `feat/xxx`/`fix/xxx`，PR 目标分支 `dev`（[modality-commit](.agents/skills/modality-commit/SKILL.md) / [modality-branch-merge](.agents/skills/modality-branch-merge/SKILL.md)）
- **模块三处同步**：`settings.gradle` include、模块 `build.gradle`、boot 聚合依赖（[modality-new-module](.agents/skills/modality-new-module/SKILL.md)）

高危改动面（租户一致性、鉴权 401/403、签名验签、文件上传下载、Webhook 外呼 SSRF、限流）必须带负向用例；审查口径见 [modality-code-review](.agents/skills/modality-code-review/SKILL.md)。

## 开发规范（标准 skills）

`.agents/skills/java-*` 是《Java 开发手册（黄山版）》拆分的 7 个独立标准技能，写码、重构、建表、审查时按需引用：

| 维度 | skill |
| --- | --- |
| 编程规约（命名 / 常量 / 格式 / OOP / 日期时间 / 集合 / 并发 / 控制语句 / 注释 / 前后端） | [java-coding-standards](.agents/skills/java-coding-standards/SKILL.md) |
| 设计规约 | [java-design-standards](.agents/skills/java-design-standards/SKILL.md) |
| 异常日志（错误码 / 异常处理 / 日志） | [java-exception-logging](.agents/skills/java-exception-logging/SKILL.md) |
| 单元测试 | [java-unit-testing](.agents/skills/java-unit-testing/SKILL.md) |
| MySQL 数据库（建表 / 索引 / SQL / ORM） | [java-mysql-database](.agents/skills/java-mysql-database/SKILL.md) |
| 工程结构（应用分层 / 二方库 / 服务器） | [java-project-structure](.agents/skills/java-project-structure/SKILL.md) |
| 安全规约 | [java-security-standards](.agents/skills/java-security-standards/SKILL.md) |

优先级：**仓库硬性规则 > 标准技能**；冲突处的取舍与重构决策点集中在 [modality-code-design](.agents/skills/modality-code-design/SKILL.md) 与 [modality-layout](.agents/skills/modality-layout/SKILL.md)。

## 工作流（skills）

按任务取用，不要凭记忆重写：

| 场景 | skill |
| --- | --- |
| 写码 / 重构前查开发规范 | [java-coding-standards](.agents/skills/java-coding-standards/SKILL.md) 等 7 个标准技能（见上表） |
| 提交验证选择 / 排查验证失败 | [modality-pre-push-checks](.agents/skills/modality-pre-push-checks/SKILL.md) |
| 写提交 / 拆提交粒度 | [modality-commit](.agents/skills/modality-commit/SKILL.md) |
| 切分支 / 开 PR / 合并 | [modality-branch-merge](.agents/skills/modality-branch-merge/SKILL.md) |
| 写或改测试 | [modality-testing](.agents/skills/modality-testing/SKILL.md) |
| 审查改动（自审或审他人） | [modality-code-review](.agents/skills/modality-code-review/SKILL.md) |
| 启动 / 接口 / 测试失败排障 | [modality-troubleshooting](.agents/skills/modality-troubleshooting/SKILL.md) |
| 目录 / 包结构 / 重构分层 | [modality-layout](.agents/skills/modality-layout/SKILL.md) |
| 写或重构 Java 代码（对齐阿里手册） | [modality-code-design](.agents/skills/modality-code-design/SKILL.md) |
| 新增库 / 业务模块 / 业务服务 | [modality-new-module](.agents/skills/modality-new-module/SKILL.md) |
| 业务模块新增 REST 端点 | [modality-new-endpoint](.agents/skills/modality-new-endpoint/SKILL.md) |
| 新增 / 变更数据库表 | [modality-new-table](.agents/skills/modality-new-table/SKILL.md) |
| 设计跨模块 SPI | [modality-spi](.agents/skills/modality-spi/SKILL.md) |
| 设计错误语义 / 错误码 | [modality-error-handling](.agents/skills/modality-error-handling/SKILL.md) |
| 写升级与回滚说明 | [modality-upgrade-notes](.agents/skills/modality-upgrade-notes/SKILL.md) |
| 改 README / 仓库文案 | [modality-readme-doc](.agents/skills/modality-readme-doc/SKILL.md) |
| 构建镜像 / 部署 / 冒烟 | [modality-release-deploy](.agents/skills/modality-release-deploy/SKILL.md) |
| 新增 / 调整架构规则 | [modality-arch-rules](.agents/skills/modality-arch-rules/SKILL.md) |
| 扫描简化机会 | [modality-find-simplifications](.agents/skills/modality-find-simplifications/SKILL.md) |
| 归档 / supersede 笔记 | [modality-archive-agent-notes](.agents/skills/modality-archive-agent-notes/SKILL.md) |

技能格式、索引与模板见 `.agents/skills/README.md`；新建技能从 `.agents/templates/skill.md` 起步。

## Agent Notes（决策记录）

- 只为「代码、测试与现有文档解释不了」的**持久决策依据**写；机械性、局部改动豁免
- 路径编码 `{lifecycle}/{class}/yyyy-mm-dd-topic.md`：`proposed` / `implemented` / `rejected` / `archived`
- 每条新笔记触发一次 supersession 检查；`archived/` 冻结，永不编辑
- 格式全文：`.agents/notes/README.md`；骨架模板：`.agents/templates/note-*.md`；归档操作：`.agents/skills/modality-archive-agent-notes/`
- 体系自身的维护约定：`.agents/notes/implemented/process/2026-10-16-agent-system-maintenance.md`

## 编辑本文件

保持每条规则自包含、能独立成立；细节下沉到 skill 或 note，并把相对链接留在规则里；能精简就不要加长。改规则时同步检查引用它的 skill 与 README。
