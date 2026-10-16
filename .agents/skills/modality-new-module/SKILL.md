---
name: modality-new-module
description: 在 modality 新增库、业务模块或业务服务时使用，覆盖三处构建同步、包名与插件约定、自动配置骨架、落库接线、白名单登记与验收测试登记。
---

# 新增模块

> 工程结构标准（应用分层 / 二方库 / 服务器）见 [java-project-structure](../java-project-structure/SKILL.md)。

先定形态：**能做成库就不做业务模块**；需要 HTTP 端点的才叫业务模块；非通用能力、独立部署单元放 `modality-services/`，与业务模块同层、同样只能依赖库。

## 步骤

1. **构建三处同步**（缺一不可）：
   - `settings.gradle`：`include 'modality-library:modality-library-spring-boot-<x>'`（modules / services 换对应前缀）
   - 模块 `build.gradle`：插件 `id 'modality.spring-library-conventions'`；`description` 写中文短名；依赖用 `project(':...')` 与 `libs.*`，不写版本字面量
   - `modality-boot/build.gradle`：聚合 `implementation project(...)`
2. **包名**：库 `cn.slainc.<语义>`（`api-key` → `cn.slainc.apikey`）；业务模块 `cn.slainc.modules.<x>`；业务服务 `cn.slainc.services.<x>`（live-monitor → `cn.slainc.services.livemonitor`）
3. **自动配置骨架**：`autoconfigure` 包内 `@AutoConfiguration` + `@ConditionalOnProperty(prefix = "modality.<x>", name = "enabled", matchIfMissing = true)` + `@EnableConfigurationProperties(XProperties.class)`；每个 Bean `@ConditionalOnMissingBean`；登记 `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
4. **业务模块目录**：必备 `autoconfigure` / `service/<域>`（直接类，禁止 `impl` 子包）/ `web/<域>` / `entity` 与根包 `XxxKeys`；落库 `mapper` 或 `store` 至少其一；按需 `config`/`dto`/`excel`/`support`/`listener`/`publisher`/`scheduler`/`aspect`/`filter`/`context` 等（细则见 [modality-layout](../modality-layout/SKILL.md)）
5. **落库接线**（业务模块）：仿 `UpmsMybatisAutoConfiguration`——`@AutoConfigureBefore(name = "cn.slainc.mybatis.autoconfigure.MybatisAutoConfiguration")`、`@MapperScan`、`ClasspathSchemaInitializer(dataSource, "<x>-schema.sql")`、平台级表白名单（`TenantIgnoreTables.merge`）
6. **DDL**：`db/<x>-schema.sql`，幂等、H2 / MySQL 8 双兼容（细则见 [modality-new-table](../modality-new-table/SKILL.md)）
7. **验证**：
   - `./gradlew :<module-path>:test`
   - `./gradlew :modality-boot:test --tests 'cn.slainc.boot.ArchitectureTest'`
   - 有新端点的在 boot 补 `Batch*AcceptanceTest`（编号顺延）
8. **提交拆分**：实现与测试分开提交（`feat(scope): ...` + `test(scope): ...`）

## 参考模板

| 形态 | 模板 |
| --- | --- |
| 库 | `modality-library-spring-boot-tenant`（解析器组合、白名单、指标） |
| 业务模块（最全） | `modality-modules-upms`（SPI、数据权限、日志、Excel） |
| 轻量业务模块 | `modality-modules-job` |
| 业务服务 | `modality-services-live-monitor`（装配骨架） |
