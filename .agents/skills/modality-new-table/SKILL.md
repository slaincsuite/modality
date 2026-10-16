---
name: modality-new-table
description: 在 modality 新增或变更数据库表时使用，覆盖 db/*-schema.sql 幂等 DDL、命名与公共列、租户/平台表归属判定、实体与 Mapper 对齐、H2 与 MySQL 双兼容验证。
---

# 新增 / 变更数据库表

> 建表 / 索引 / SQL / ORM 通用条款见 [java-mysql-database](../java-mysql-database/SKILL.md)；本技能是其在 modality 的落地口径。

## 先定租户归属（fail-closed）

- **租户业务表**：必须带 `tenant_id`，行级隔离自动改写 SQL，无需登记
- **平台级表**：必须登记进租户忽略集（`TenantIgnoreTables.merge`，模式见 `UpmsTableWhitelist`），漏登记会直接报错或越权
- 拿不准时先按租户表处理；跨租户读写走 `TenantCrossAccess`

## DDL

- 文件：`db/<module>-schema.sql`（新模块要在 `*MybatisAutoConfiguration` 挂 `ClasspathSchemaInitializer`，既有模块直接追加）
- 全部 `CREATE TABLE IF NOT EXISTS`，启动幂等执行；**变更已有列**不走本文件，按发布说明人工 DDL
- 命名：表 `t_<资源>`；唯一约束 `uk_*`、索引 `idx_*`
- 公共列跟随既有表：`tenant_id`（租户表必带）、`deleted`、`create_time` / `update_time`；需要乐观锁的加 `version`
- 兼容：dev 跑 H2（`MODE=MySQL`）+ 生产 MySQL 8；避免方言函数，字符集 utf8mb4

## 代码对齐

- 实体：`@TableName(XxxRecord.TABLE_NAME)`，`TABLE_NAME` 常量在实体上定义并引用模块 `*Keys` 的表名常量（模式：`UserRecord` / `TenantPlanRecord`）
- Mapper：接口放 `mapper` 包（`@MapperScan` 已覆盖）；复杂 SQL 与租户拦截器共存时先验证改写结果
- 敏感列加 `@Sensitive` 注解

## 验证

1. `./gradlew :<module-path>:test`——dev H2 会真实建表并跑 CRUD
2. 涉及端点 / 装配的跑对应 `Batch*AcceptanceTest`，平台级表重点验证「未误入租户过滤、未越权」
3. PR 描述写明升级与回滚方式（新表幂等；变列需人工 DDL）
