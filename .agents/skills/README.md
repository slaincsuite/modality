# Skills

代理技能目录：每个技能一个子目录，内含 `SKILL.md`。分两类：

- **开发规范（标准技能）**：`.agents/skills/java-*`——《Java 开发手册（黄山版）》的独立落地，任何写码 / 重构 / 审查场景可直接引用
- **项目技能**：`.agents/skills/modality-*`——modality 特有的工作流与口径

新技能从 `.agents/templates/skill.md` 复制骨架。

## 文件格式

```markdown
---
name: java-<name>（标准技能）/ modality-<name>（项目技能）
description: <什么时候使用：写明触发条件，一段话>
---

# <中文标题>

> 依据行（标准技能标注手册来源；项目技能标注引用的标准技能）

（中文正文：步骤、命令、检查清单、验证方式、已知坑）
```

- `name`、`description` 两个 frontmatter 字段必须保留（英文键名）；`description` 决定技能何时被加载，写成触发条件而不是功能简介
- 技能正文全中文；标题直接用中文语义
- 一个技能只解决一类任务；跨技能引用用相对链接（`../xxx/SKILL.md`），不要裸文字
- 与 `AGENTS.md` 分工：AGENTS.md 是规则（自包含、短）；技能是流程（可长、可操作）

## 开发规范（标准技能）

| 技能 | 维度 |
| --- | --- |
| `java-coding-standards` | 编程规约：命名 / 常量 / 格式 / OOP / 日期时间 / 集合 / 并发 / 控制语句 / 注释 / 前后端 |
| `java-design-standards` | 设计规约 |
| `java-exception-logging` | 错误码 / 异常处理 / 日志规约 |
| `java-unit-testing` | 单元测试 |
| `java-mysql-database` | 建表 / 索引 / SQL / ORM |
| `java-project-structure` | 应用分层 / 二方库依赖 / 服务器 |
| `java-security-standards` | 安全规约 |

维护口径：依据《Java 开发手册（黄山版）》整理，随仓库版本管理；更新时整体替换对应技能。**优先级：仓库硬性规则（README / AGENTS.md）> 标准技能**；两者冲突处的取舍与重构决策点见 `modality-code-design` / `modality-layout`。

## 项目技能索引

| 技能 | 用途 |
| --- | --- |
| `modality-pre-push-checks` | 提交 / 推送 / 宣布完成前，按改动面选最小验证集合 |
| `modality-commit` | 提交消息格式、粒度拆分与提交前检查 |
| `modality-branch-merge` | 分支模型、PR 流程、合并与冲突处理 |
| `modality-testing` | 测试选层、H2 / 真 Redis 基建、Batch 验收与负向用例（标准：java-unit-testing） |
| `modality-code-review` | 审查 modality 改动：租户安全、分层、装配、测试惯例（标准：java-security-standards / java-coding-standards） |
| `modality-troubleshooting` | 启动 / 接口 / 测试失败排障 |
| `modality-layout` | 目录与包结构事实 + 决策点（标准：java-project-structure / java-mysql-database） |
| `modality-code-design` | 装配风格、命名映射、已核实偏差与决策点（标准：java-coding-standards / java-design-standards） |
| `modality-new-module` | 新增库 / 业务模块 / 业务服务 |
| `modality-new-endpoint` | 业务模块新增 REST 端点 |
| `modality-new-table` | 新增 / 变更数据库表（标准：java-mysql-database） |
| `modality-spi` | 跨模块能力下沉：接口 + 模型 SPI |
| `modality-error-handling` | 错误语义、异常选型与错误码（标准：java-exception-logging） |
| `modality-upgrade-notes` | 升级与回滚说明（表 / 配置 / 安全变更） |
| `modality-readme-doc` | README 与仓库级文案编辑规范 |
| `modality-release-deploy` | 镜像构建、compose 部署、冒烟与回滚 |
| `modality-arch-rules` | 新增 / 调整 ArchUnit 架构规则 |
| `modality-find-simplifications` | 扫描简化机会（死代码、重复注册、过度包装） |
| `modality-archive-agent-notes` | Notes 的 supersession 检查与归档 / 删除操作 |

## 配套资产

- `.agents/templates/`：skill / note / PR 描述骨架
- `.agents/notes/`：决策记录（何时写、格式、归档规则见其 README）

新增技能后，把入口补进本 README 与根 `AGENTS.md`；规则与同步点的维护约定见 `../notes/implemented/process/2026-10-16-agent-system-maintenance.md`。
