# Agent Note: 代理工作体系维护约定

Status: implemented

## Problem

AGENTS.md + skills + notes 三层体系会随仓库演进漂移：技能与索引进出不一致、规则变化后引用处过期、笔记事实失同步。没有维护约定时，体系会逐渐失去可信度——而不可信的指南比没有指南更糟。

## Decision

维护责任按改动类型划分，全部随仓库版本管理（2026-10-16 起入库，见 [代理工作文件纳入版本管理](2026-10-16-agent-files-versioned.md)）：

- **技能变更**（新增 / 改名 / 删除 / 职责调整）：同一次改动里更新 `.agents/skills/README.md` 索引与根 `AGENTS.md`「工作流」表；检查其他 skill 对它的相对链接
- **AGENTS.md 规则变更**：同步检查引用它的内容（如库内禁用包名清单在 `modality-layout`、README 各有一份表述）
- **笔记纪律**：沿用 notes README——新笔记触发 supersession 检查；implemented 笔记与代码事实保持同步（路径、命名、默认值）；archived 冻结
- **模板**：`.agents/templates/` 承载 skill / note / PR 描述骨架；改格式先改模板，再让存量跟随
- **事实复核时机**：涉及构建、测试、部署的较大改动落地后，抽查高频技能（pre-push-checks、testing、troubleshooting、release-deploy）的命令与表格是否仍与仓库一致

## Alternatives considered

- **机械 gate（脚本校验索引与链接）**：仓库无 CI 与 doc-gate 基建；一次性的链接检查脚本足够，放弃常驻脚本。
- **不设维护约定**：接受自然漂移，但体系价值随漂移归零，放弃。

## Consequences

- 技能有单一索引（skills/README）与单一入口（AGENTS.md 表），漏登记的概率降低
- 规则事实存在少量同步点（AGENTS.md、layout 技能、README），靠每次改动自觉执行，没有自动化兜底
- 相对链接可用一次性脚本全量检查（rg 配合简单正则即可），无需常驻设施
