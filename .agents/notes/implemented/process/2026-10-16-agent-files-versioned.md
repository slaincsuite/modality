# Agent Note: 代理工作文件纳入版本管理

Status: implemented

## Problem

代理工作体系（`AGENTS.md`、`.agents/` 技能、笔记、模板）自建立起按「本地不入库」执行（[被取代的本地工作流笔记](2026-10-16-local-agent-workflow.md)）：换机器、换会话需要人工搬运，规范与决策容易在副本之间漂移，多人协作时无法共享同一份规则。2026-10-16 决定改变存储位置，需要明确入库边界与同步口径。

## Decision

2026-10-16 起，`AGENTS.md` 与 `.agents/`（skills / notes / templates）纳入版本管理：`.gitignore` 移除 `AGENTS.md` 与 `.agents/` 忽略规则；改动随常规分支流程提交（`chore/` 等分支，PR 目标 `dev`）。

边界与纪律：

- 不写入任何凭据、token 与个人信息；提交前按 [modality-commit skill](../../../skills/modality-commit/SKILL.md) 复核 stage
- `java-*` 标准技能为《Java 开发手册（黄山版）》的仓库副本，更新时整体替换并保持来源标注
- 全部已入库文档中「已被 .gitignore 忽略 / 不要入库」的旧表述同步更新（`AGENTS.md`、`modality-commit`、skills README、维护约定笔记）

## Alternatives considered

- **保持本地不入库**（原决策）：避免仓库讨论成本，但分发与同步靠人工搬运、多会话之间漂移；被本决定取代。
- **仅入库 `AGENTS.md`，`.agents/` 继续本地**：规则与技能 / 笔记分属两处，同步点增加，引用链接天然失效；放弃。

## Consequences

- 规则、规范技能与决策记录随仓库分发，任何会话 / 协作者开箱一致；notes 的 supersession 与归档纪律继续生效
- 仓库体积与可见面增加（标准技能约 130KB 文本）；手册版权归阿里巴巴，《手册》允许免费传播，若后续有合规要求可将 `java-*` 技能移出仓库
- 维护点收敛：技能索引、AGENTS.md 工作流表、notes 纪律三处的同步责任不变（见 [维护约定](2026-10-16-agent-system-maintenance.md)）
