# Agent Notes

本目录记录影响本仓库的持久决策——代码、测试与常规文档承载不了的「为什么」与「放弃了什么」。本文件定义笔记放哪、何时写、写成什么格式。

## 路径编码

每条笔记的路径同时编码两个维度：`{lifecycle}/{class}/yyyy-mm-dd-topic-title.md`。

**lifecycle（顶层目录）= 状态**，随状态迁移：

| lifecycle | 含义 |
| --- | --- |
| `proposed/` | 提案，尚未实施（或只实施了一部分） |
| `implemented/` | 已随代码落地；**与代码保持同步**：后续代码改了路径 / 命名 / 默认值，同期更新笔记里的事实描述（只更新事实，不改写决定） |
| `rejected/` | 已否决；只在其理由能防止后来者重蹈时保留，否则连同文件删除 |
| `archived/` | 冻结的历史存档：**永不编辑、不翻译、不重排，也不作为现状依据** |

**class（二级目录）= 决策类别**，封闭集，不许自造：

| class | 覆盖 |
| --- | --- |
| `feature` | 新的用户 / 接口可见能力 |
| `bug-fix` | 修复缺陷或后验问题 |
| `simplification` | 删除代码 / 行为 / 面积，不新增能力 |
| `architecture` | 已发布源码的结构决策：包关系、运行期词汇 |
| `process` | 代码外围的工具、策略与流程 |
| `testing` | 测试基建与策略 |

文件名日期 = 主题**首次提出**日（以 git 历史为准）；主题用英文短横线，正文全中文。笔记之间用相对链接互引，形如 `[主题](../../implemented/architecture/2026-01-01-xxx.md)`，不要裸文字引用。**不要建集中索引**：路径本身即索引，集中文件只会制造无谓冲突。

## 何时写

- 只为「一次性代码、测试与现有文档都解释不了」的持久决策依据而写；机械性、局部改动豁免
- 已实施的决定进 `implemented/`；重大未竟工作先开 `proposed/`
- 已有笔记拥有该决定时**更新它**，不新建重复；决定被推翻时用新笔记 supersede 并互相链接，不要把旧笔记改写成另一种决定
- **每条新笔记触发一次 supersession 检查**：搜活跃树里覆盖同一决策的旧笔记；完全取代且已实施的，在同一次改动里归档；部分取代的保持活跃并交叉链接

## 文件格式

```markdown
# Agent Note: <标题>

Status: <proposed | implemented | rejected — 一句话原因>

## Problem

（动机，先于方案独立成立）
```

- `Status:` 单独一行、与所在 lifecycle 目录一致，不带日期；除 `rejected` 外不带内容
- 正文必备 `## Problem` 开头和 `## Alternatives considered` 收尾/穿插——每个真实备选一段，写清为什么没选它；备选是记录，不是事后编造
- 骨架模板：`../templates/note-proposed.md` / `note-implemented.md` / `note-rejected.md`，新笔记从对应模板复制
- 两个骨架（自定技术章节插在中间，章节名用完整语义）：
  - `proposed/`：`## Problem` → `## Proposal` → … → `## Alternatives considered` → `## Acceptance criteria` → `## Risks`
  - `implemented/`：`## Problem` → `## Decision`（现在时，描述已发布现实）→ … → `## Alternatives considered` → `## Consequences`（代价与收获）
- `implemented/` 不接受提案时态章节名（`## Proposal`、`## Plan`、`## Migration plan`、`## Acceptance criteria`）；`## Testing`、`## Related` 等当前事实章节可以
- `rejected/`：保留提案时的原文原样，结论只体现在 `Status:` 行

## 状态迁移

- `proposed/` → `implemented/`：同一次改动里改 `Status:`、把 `## Proposal` 重写为现在时 `## Decision`、验收标准与风险并入 `## Consequences`
- 归档：决定已完结、理由难再指导未来工作、但仍有历史价值的 implemented 笔记，移入 `archived/`（路径去掉 `implemented` 一级），仅在 `Status:` 下加一行 `Archived: YYYY-MM-DD`，此后冻结；入档改动只允许这处内容变化
- 绝不归档 proposed 笔记：过时提案走 `rejected/` 或删除

## 语言

正文全中文，与仓库文案一致；`# Agent Note: ` 前缀、`Status:` 行与 `Archived:` 行的关键字保持英文原样，便于检索。
