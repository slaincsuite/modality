# Agent Note: 本地代理工作流（AGENTS.md + skills + notes）

Status: implemented

## Problem

仓库需要一个面向 AI 代理的常驻工作层：哪些命令要跑、哪些规则不能破、坑在哪里、为什么这么决定。README 面向人类读者，体量大且不覆盖代理执行细节；把执行细节散落在每次对话里，既不可复制也不可积累。

## Decision

参照 deepseek-harness 的三层结构，在仓库根引入**本地、不入库**的代理工作文件（`.gitignore` 已忽略 `AGENTS.md` 与 `.agents/`）：

- `AGENTS.md`：规则层。自包含的硬性约定，细节下沉到 skills / notes 并留相对链接
- `.agents/skills/`：工作流层。每个技能一个目录，内含 `SKILL.md`（frontmatter `name` + 触发条件式 `description`）；清单以 `.agents/skills/README.md` 索引为准（初始五条，已扩展覆盖提交、测试、排障、SPI、发布等）
- `.agents/templates/`：骨架模板层。skill / note / PR 描述三类模板
- `.agents/notes/`：决策层。路径编码 `{lifecycle}/{class}/yyyy-mm-dd-topic.md`；`## Problem` 开头、`## Alternatives considered` 必填；`archived/` 冻结

三条纪律：笔记只为持久决策依据而写（机械改动豁免）；每条新笔记做一次 supersession 检查；`implemented/` 笔记与代码事实保持同步。

存储位置决策（本地不入库）已于 2026-10-16 被 [代理工作文件纳入版本管理](2026-10-16-agent-files-versioned.md) 取代；结构与纪律部分继续有效。

## Alternatives considered

- **全部塞进一个 AGENTS.md**：规则与流程混在一起，文件膨胀后没人读完。deepseek-harness 按「规则 / 流程 / 依据」三层拆分更耐久，采纳其结构。
- **照搬 deepseek-harness 全套（双语 sidecar、机械 gate、冻结清单脚本）**：本仓库没有 CI 与 doc-gate 基建，且文案本来就全中文，双语对照与校验脚本是纯负担。只保留其设计精神，去掉机械化外围。
- **英文笔记**：与仓库「文案全中文」的硬性约定冲突，放弃。
- **提交入库**：代理工作文件带个人偏好，入库会增加仓库讨论成本；选择本地保留，代价是换机器需自行搬运。

## Consequences

- 代理入口统一：新会话读 `AGENTS.md` 即可获得全部规则与技能索引，不再依赖口口相传
- 关键坑被固定下来（Redis 密码、H2 不在 fat jar、租户白名单 fail-closed、字段严格分层），减少重复踩坑
- 笔记格式与 deepseek-harness 对齐，未来若要引入机械校验（格式 gate、冻结清单）有现成参照
- 需要纪律维持：归档冻结、supersession 检查靠每次改动的自觉执行，没有脚本兜底
