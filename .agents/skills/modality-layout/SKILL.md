---
name: modality-layout
description: 在 modality 重构工程结构、决定新文件或新包位置时使用：工程结构与数据库标准在 java-project-structure / java-mysql-database 独立技能中，本技能承载 modality 的目录与包结构事实（顶层布局、库/模块包、测试、文件放置决策表）与决策点。
---

# 目录结构与包结构规范（modality 口径）

工程结构与数据库标准已拆分为独立技能：

| 维度 | 标准技能 |
| --- | --- |
| 应用分层 / 二方库依赖 / 服务器 | [java-project-structure](../java-project-structure/SKILL.md) |
| 建表 / 索引 / SQL / ORM | [java-mysql-database](../java-mysql-database/SKILL.md) |

**优先级：仓库硬性规则 > 标准技能。** 本技能只写 modality 特有结构事实与差异处理；方向性差异集中在文末决策点。

## 重构执行流程

1. 按维度扫描：分层映射 → 包结构 → 依赖治理 → 建表规范
2. 结构调整单独立项（跨模块移动、改名先跑 `ArchitectureTest` 基线）；与业务改动分开提交
3. 建表 / DDL 改动细则见 [modality-new-table](../modality-new-table/SKILL.md)

## 应用分层映射（标准 ↔ modality）

| 标准分层 | modality 对应 | 说明 |
| --- | --- | --- |
| 开放接口层 | 业务模块 `web/`（HTTP）+ 库内 `spi` 包 | 端点只在业务模块 `web` 包 |
| Web 层 | 业务模块 `web/<域>/` | 只做访问控制转发、参数校验，不写业务 |
| Service 层 | 业务模块 `service/<域>/` + 库内服务类 | 业务逻辑与事务所在层 |
| Manager 层（通用业务处理） | **无独立层**——通用能力一律下沉为库（`support`/`spi`） | 标准推荐层，仓库以库化替代，保持 |
| DAO 层 | 业务模块 `mapper/`（MyBatis）或 `store/`（非 MP 存储） | 与数据源交互 |

分层领域模型（DO/DTO/BO/QUERY/VO ↔ Record/Request/Form/domain/View）与命名细则见 [modality-code-design](../modality-code-design/SKILL.md)。

依赖方向（由 `ArchitectureTest` 强制）：web → service → mapper/store；库 → 更底层库；业务模块互相禁止依赖，跨模块走「接口 + 模型」SPI（[modality-spi](../modality-spi/SKILL.md)）。分层异常处理：DAO 抛、Service 记录、Web 顶层 `GlobalExceptionHandler` 统一出参（[modality-error-handling](../modality-error-handling/SKILL.md)）。

## 包结构总则

- 包路径与模块一一对应：库 `cn.slainc.<语义>`（`api-key` → `cn.slainc.apikey`）；业务模块 `cn.slainc.modules.<x>`；业务服务 `cn.slainc.services.<x>`
- 【标准】包名全小写、单数、每段一个自然语义词；禁止缩写堆叠
- 业务模块内部按**资源域**分子包：`service/<域>/`、`web/<域>/`（`user`、`role`、`plan`、`webhook`…）
- 模块常量类 `XxxKeys` 放模块根包（表名、权限码、状态值）；实体持 `TABLE_NAME` 常量并引用 `XxxKeys`
- 库内实现类放技术词包或领域词包；禁用模块独占包名；测试包镜像 main

## 顶层：谁放什么

| 位置 | 放什么 | 约束 |
| --- | --- | --- |
| `modality-library/` | 可复用能力：自动配置 + 服务 + SPI | 不注册端点；不依赖业务模块 |
| `modality-modules/` | 业务模块：端点 + 业务服务 + 落库 | 模块间零依赖；跨模块下沉「接口 + 模型」SPI |
| `modality-services/` | 独立业务服务：与业务模块同层 | 只依赖库；包名 `cn.slainc.services.<x>` |
| `modality-boot/` | 聚合装配与配置、整机验收测试 | 不含业务端点 |
| `db/` | 各模块 `*-schema.sql`（幂等 DDL） | 启动自动执行；变更已有列走人工 DDL |
| `buildSrc/` | Gradle 约定插件 | 改构建约定先看这里 |

## 库内包结构（`cn.slainc.<语义>`）

- `autoconfigure`：`@AutoConfiguration` 类与 `*Properties`；登记 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- 实现类放**技术词包或领域词包**：`support` / `loader` / `dao` / `domain` / `spi` / `config` / `enums` / `metrics` / `event` / `template` / `store` / `aspect` / `guard` / `annotation`，或领域词（`resolver` / `isolation` / `lookup` / `routing` / `dedup` / `quota` 等）
- **禁用包名**（ArchUnit 强制）：`service`/`web`/`dto`/`entity`/`mapper`/`model`/`impl`/`vo`/`form`/`controller`/`repository`/`api`/`admin`（`cn.slainc.web` 库本体豁免）

## 业务模块包结构（`cn.slainc.modules.<x>`）

**必备**：

```
<x>/
├── XxxKeys.java            # 表名、权限码、状态值常量（final + 私有构造）
├── autoconfigure/          # 装配、*Properties、白名单类、ClasspathSchemaInitializer 接线
├── entity/                 # XxxRecord（@TableName(TABLE_NAME)）
├── service/<域>/           # 业务服务：直接类（见决策点 1）
└── web/<域>/               # REST 控制器：XxxManageController / XxxController
```

**落库**：`mapper`（MyBatis，`@MapperScan` 覆盖）或 `store`（非 MP 存储）至少其一——auth、policy 用 store 无 mapper。

**常见按需**：`config` / `dto` / `excel` / `support` / `store` / `listener` / `publisher` / `scheduler` / `aspect` / `filter` / `context` / `security` / `datascope` / `log` 及领域子包（upms 的 `datascope`/`log`、auth 的 `mfa`/`qrcode`/`third`、audit 的 `alert`、infra 的 `context`）。

业务服务（`modality-services/`）同构，只能依赖库。

## 依赖治理（对照 java-project-structure）

- 版本单一来源：`gradle/libs.versions.toml`（达标：模块禁写版本字面量、无同 GA 多版本）
- 版本仲裁集中在 BOM（`modality-library-spring-boot-bom`，对标 dependencyManagement）
- 二方库发布物精简：只含 API / 模型 / Utils / 常量 / 枚举
- 抽查命令：`./gradlew dependencies --configuration runtimeClasspath`；锁文件不入库

## MySQL 差异（对照 java-mysql-database）

DDL 文件：`db/<module>-schema.sql`（幂等 `CREATE TABLE IF NOT EXISTS`），启动经 `ClasspathSchemaInitializer` 执行；**变更已有列**不在此文件，按 [modality-upgrade-notes](../modality-upgrade-notes/SKILL.md) 走人工 DDL。

| 标准条款 | 仓库现状 | 处理 |
| --- | --- | --- |
| 必备三字段 `id` / `create_time` / `update_time` | 存在性达标（模块表 BIGINT 毫秒；`MybatisBaseEntity` 系为 `created_at/updated_at DATETIME`） | 双体系统一属独立 DDL 项目（决策点 3） |
| 是与否字段 `is_xxx` + unsigned tinyint | `deleted` TINYINT（0/1）、`status` 字符串枚举 | 存量不动；**新增**布尔列对齐标准（含 is_xxx 评估） |
| 索引 `uk_` / `idx_` 命名 | 达标（如 `uk_user_login`） | 新索引沿用 |
| decimal / varchar ≤5000 / 文本用 text / 禁保留字 / 表名小写非复数 | 按标准执行 | 新增 DDL 逐条核对 |

更完整的建表/索引要点与模板见 [modality-new-table](../modality-new-table/SKILL.md)。

## 测试目录

- 库 / 模块测试：各自 `src/test/java`，包结构镜像 main；模块测试专用 `src/test/resources/application.properties`（H2 + Redis database 编号隔离，见 [modality-testing](../modality-testing/SKILL.md)）
- 整机验收：`modality-boot/src/test/java/cn/slainc/boot/`——`Batch*AcceptanceTest`（编号顺延）+ 配对 `Batch*AcceptanceController`；HTTP 样板 `AcceptanceHttpSupport`
- 架构规则：`modality-boot/src/test/java/cn/slainc/boot/ArchitectureTest.java`（见 [modality-arch-rules](../modality-arch-rules/SKILL.md)）

## 文件放置决策表

| 要放的东西 | 位置 |
| --- | --- |
| REST 端点 | 业务模块 `web/<域>/` |
| 业务逻辑 / 事务 | 业务模块 `service/<域>/` |
| 请求 / 响应模型 | 业务模块 `dto/`（record） |
| 实体 / Mapper / DDL | `entity/` / `mapper/` / `db/<module>-schema.sql` |
| 跨模块能力 | 库内「接口 + 模型」SPI，禁止模块互相 import |
| 自动配置 / 属性 | 库或模块 `autoconfigure`（模块也可 `config`，跟随现状） |
| 表 / 权限码常量 | 模块根 `XxxKeys`；表名以实体 `TABLE_NAME` 为入口 |
| 开发规范 / 项目技能 / 笔记 / 模板 | `.agents/skills/java-*`（标准）/ `.agents/skills/modality-*`（项目）/ `.agents/notes/` / `.agents/templates/` |

## 重构决策点（仓库 ↔ 标准）

| # | 标准条款 | 仓库现状 | 重构推荐 |
| --- | --- | --- | --- |
| 1 | 【强制】Service/DAO 暴露接口、实现类 Impl 后缀（SOA） | 全仓库直接类、无接口 | **保持直接类**（接口只在有 SPI / 多实现时建）；如对齐标准属重大架构改造，单独立项评估 |
| 2 | 标准默认 Manager 层 | 无此层，通用能力库化 | 保持仓库口径（SPI + 库承担） |
| 3 | 三字段 datetime；布尔 is_xxx tinyint | 时间字段双体系；`deleted`/`status` 现状 | 存在性达标；新增列对齐标准；双时间体系统一先立笔记再动（独立 DDL 项目） |
| 4 | 【强制】线上不依赖 SNAPSHOT | 仓库自身 `0.0.1-SNAPSHOT` | 发布体系范畴：建立正式版本流程后执行（发版机制尚未立项） |
