# Agent Note: 变更审计内置 JDBC 落库

Status: implemented

## Problem

字段级变更审计此前只有切面与 SPI：`ChangeRecorder` 在仓库内只有 `NoopChangeRecorder`（默认吞掉记录）与 `AsyncChangeRecorder`（包装器），9 个业务模块零实现，`db/*-schema.sql` 里也没有对应表。整机跑起来是「切面采了差异、没地方存」——功能看起来齐了，实际一条记录都落不了库，是最容易踩空的一类「有接口无实现」。

## Decision

2026-10-16 在变更审计库（`modality-library-spring-boot-change-audit`）内置 JDBC 落库：

- `dao/JdbcChangeRecorder` 用 `JdbcTemplate` 整行写入 `t_change_audit_record`；落库失败只记错误日志，不反噬业务（审计失败不阻断提交）
- 表 DDL 进 `db/change-audit-schema.sql`（幂等 `CREATE TABLE IF NOT EXISTS`），由库内 `ChangeAuditJdbcAutoConfiguration` 的 `ClasspathSchemaInitializer` 在启动时执行
- 装配条件：`modality.change-audit.store=jdbc|noop`（默认 `jdbc`，需 `JdbcTemplate` 在场，否则保持 Noop）、`jdbc.table-name` 可换表名、`jdbc.auto-create-table=false` 可关闭自动建表；`@ConditionalOnMissingBean(ChangeRecorder.class)` 允许使用方提供自己的实现
- 该表是平台级表：`ChangeAuditTableWhitelist` 按 `TenantIgnoreTables.merge` 登记进租户忽略集（模式同 `ApiKeyTableWhitelist`），记录里保留 `tenant_id` 供排查，但读写不参与行级隔离
- 异步开关沿用既有的 `modality.change-audit.async`（默认关，事务提交后同步落库）

## Alternatives considered

- **只留 SPI，落库交给使用方**（原状）：接口齐、实现空，接不了地也测不到；被本决定取代。
- **懒建表（`JdbcThirdAccountBindingStore` 风格）**：库自己建表不落 `db/`，运维与 DBA 看不到 DDL，也和「新表进 `db/*-schema.sql`」的仓库口径冲突；放弃。
- **表带 `tenant_id` 行走行级隔离**：变更审计是平台级审计，运维需要跨租户按实体排查；行级隔离会让平台侧看不到全量；放弃，改登记忽略集。
- **开启异步（`async=true`）当默认**：异步线程池多一份丢记录的窗口，且默认值改动属于行为变化；保持默认同步，需要时由配置打开。

## Consequences

- 代价：启用 JDBC 的应用（含 `modality-boot`）启动后多一张表与每笔审计一行写入；`modality.change-audit.store=noop` 可回到旧行为
- 收益：`@ChangeAudited` 采集的差异真正可查、可审计；落库实现随库分发，不必每个项目各写一遍
- 目前平台内还没有业务方法标注 `@ChangeAudited`（只有能力与落库），需要哪些入口计入字段级审计由各模块自行标注

## Testing

- 库：`JdbcChangeRecorderTest`（整行落库、空值兜底、写失败不抛）、`ChangeAuditTableWhitelistTest`（平台表登记与 MyBatis 缺席时跳过）、`ChangeAuditAutoConfigurationTest`（默认 JDBC + 自动建表、`store=noop`、自定义 recorder 优先、关自动建表）
- 整机：`Batch16ChangeAuditAcceptanceTest`（切面 → 差异 → 落库全链路、字段无变化不落、任意租户上下文都能落库）
