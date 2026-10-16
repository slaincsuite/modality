---
name: modality-code-design
description: 在 modality 写或重构 Java 代码时使用：通用开发规范在 java-coding-standards / java-design-standards 独立技能中，本技能只承载 modality 特有设计约定（装配风格、命名映射、仓库口径）、已核实偏差的重构动作与决策点。
---

# 代码设计规范（modality 口径）

开发规范（《Java 开发手册（黄山版）》）已拆分为独立标准技能，随时引用：

| 维度 | 标准技能 |
| --- | --- |
| 命名 / 常量 / 格式 / OOP / 日期时间 / 集合 / 并发 / 控制语句 / 注释 / 前后端 | [java-coding-standards](../java-coding-standards/SKILL.md) |
| 设计规约（系统设计、存储、扩展性） | [java-design-standards](../java-design-standards/SKILL.md) |
| 异常 / 错误码 / 日志 | [java-exception-logging](../java-exception-logging/SKILL.md) |

**优先级：仓库硬性规则（README 贡献指南、根 AGENTS.md）> 标准技能。** 本技能只写 modality 特有内容与「现状 ↔ 标准」的偏差处理；方向性差异集中在文末决策点，重构时不要按标准静默改动这些点。

## 重构执行流程

1. 先对标准技能逐章浏览，确定本批目标条款；再对代码扫描产出 findings：`文件:行` + 条款 + 建议
2. 按主题拆分提交（`refactor(scope): ...` / `style(scope): ...`），纯格式改动单独成条；行为变化必须带测试
3. 验证按 [modality-pre-push-checks](../modality-pre-push-checks/SKILL.md)；结构改动先读 [modality-layout](../modality-layout/SKILL.md)

## 仓库特有约定（标准未涉及，保持不动）

- **无构造型注解**：不用 `@Service`/`@Component`/`@Repository`，Bean 一律在 `autoconfigure` 显式 `@Bean` 声明；构造器注入 + `private final`。两种注册风格：`@Bean @ConditionalOnMissingBean(XxxController.class) @ConditionalOnBean(XxxService.class)` + `new`（job）；`@Import({XxxController.class, ...})`（upms）
- 条件注解链：`@ConditionalOnMissingBean` / `@ConditionalOnBean` / `@ConditionalOnClass(name = "...")`
- 端点注解组合：`@Operation` + `@RequirePermission(Keys 常量)` + `@Idempotent` + `@AuditLog` + `@RateLimit` + `@Valid`（样板见 job 控制器）
- 返回样式：显式 `Result.ok` 或裸对象走 `ApiResponseAdvice`，同模块一致；文案全中文

## 命名与分层映射（标准 ↔ modality，等义替换）

| 手册 | 仓库 | 示例 |
| --- | --- | --- |
| DO（数据对象） | `XxxRecord` 实体 | `UserRecord` |
| DTO | `XxxRequest` / `XxxForm`（record） | `UserCreateRequest` |
| VO | `XxxView`（record） | `TenantPlanView` |
| BO | 库内 `domain` 包 | `Tenant` |
| QUERY | 入参 record 或命名参数；禁止用 Map 传 >2 个查询参数 | — |

其他仓库命名：控制器 `XxxManageController`/`XxxController`；服务 `XxxService`（按域分子包）；常量 `XxxKeys`（final + 私有构造）；配置 `XxxProperties`（`modality.<模块>.*`）；权限码 `job:task:query` 三段式；方法动词直白（`page`/`get`/`create`/`update`/`delete`/`changeStatus`）。

## 已核实偏差与重构动作（对照标准技能）

| 偏差 | 条款（java-coding-standards） | 现状 | 重构动作 |
| --- | --- | --- | --- |
| 实体属性默认值 | 「POJO 不设属性默认值」【强制】 | `UserRecord.status`、`UserRecord.mustChangePassword`、`PermissionRecord.sort`、`Tenant.status` 等 | 逐实体评估：DB 列有 `DEFAULT` 的去掉 Java 默认值；无 `DEFAULT` 的补 DDL 或显式赋值；行为影响配测试 |
| 缺 `toString` | 「POJO 必须写 toString」【强制】 | 实体普遍未实现 | IDE 批量生成；继承时带 `super.toString()` |
| `serialVersionUID` 半覆盖 | 「不修改/显式维护 serialVersionUID」 | 序列化类约 22 处仅 11 处声明；`MybatisBaseEntity` 未声明 | 批量补齐并保持稳定值 |
| `java.util.Date` 残留 | 「日期时间用 java.time」【推荐】 | `utils`（`DateUtils`/`BeanUtils`）、`change-audit`、`quartz` 适配处 | 分两层：对外兼容入口保留 `Date` 转换，内部计算迁 `java.time` |

已达标项（重构时豁免误报）：生产代码零 `Executors`（测试并发用例豁免）；主代码零 `SimpleDateFormat`；`ThreadLocal` 上下文有 `clear()`（`TenantContext`/`UserContext`，新增上下文必须复刻）；日志走 SLF4J；并发更新有 `@Version` 乐观锁。

重构动作补充：参数 >3 个的方法按 record 收参；long 字面量补 `L`；魔法值（状态字符串/阈值）收敛为 `XxxKeys` 常量或枚举。

## 重构决策点（仓库 ↔ 标准）

| # | 标准条款 | 仓库口径 | 重构推荐 |
| --- | --- | --- | --- |
| 1 | 【强制】类/属性/方法 Javadoc、类加创建者信息 | 注释极少、禁 Javadoc、无 @author（git 追溯作者） | **保持仓库口径**；如决定全面对齐，先修订 README 与 AGENTS.md 硬性规则，再单独立项批量执行——不在普通重构中静默加 Javadoc |
| 2 | 【强制】POJO 不设属性默认值 / toString / serialVersionUID | 见上表偏差 | **对齐标准**：按上表动作执行 |
| 3 | 【强制】Service/DAO 用接口 + Impl（SOA） | 全仓库直接类、无接口 | 见 [modality-layout](../modality-layout/SKILL.md) 决策点（推荐保持直接类） |

## 交叉引用

- 异常 / 错误码 / 日志落地 → [modality-error-handling](../modality-error-handling/SKILL.md)（标准条款见 java-exception-logging）
- 测试 → [modality-testing](../modality-testing/SKILL.md)（标准条款见 java-unit-testing）
- 安全 → [modality-code-review](../modality-code-review/SKILL.md)（标准条款见 java-security-standards）
- 建表 / 结构 → [modality-new-table](../modality-new-table/SKILL.md) / [modality-layout](../modality-layout/SKILL.md)
