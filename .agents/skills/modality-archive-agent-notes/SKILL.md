---
name: modality-archive-agent-notes
description: 在 modality 写新 Agent Note、做 supersession 检查或归档、删除、拒绝旧笔记时使用，给出搜索、判定、移动、冻结与修链接的完整操作步骤。
---

# Notes 归档与 supersession

写任何新笔记前先做完这套动作；不做完不算写完。规则全文见 [notes README](../../notes/README.md)。

## 1. 搜索同主题旧笔记

```sh
rg -n "<关键词>" .agents/notes/proposed .agents/notes/implemented
```

关键词用决策主题、类名、表名、机制名，多试几组。

## 2. 判定取代关系

| 关系 | 动作 |
| --- | --- |
| 无交集 | 正常写新笔记 |
| 部分取代（旧笔记仍有一部分成立） | 两条都保持活跃，互加相对链接，旧笔记只更新事实 |
| 完全取代且旧笔记已实施 | 先写新笔记，再归档旧笔记（见下） |
| 完全取代但旧笔记是 proposed | 移入 `rejected/`（Status 行写原因）或删除——**绝不归档 proposed** |
| 拿不准 | 保持两条活跃 + 交叉链接，宁可重复不误删 |

## 3. 归档操作（implemented → archived）

1. 移动文件：`archived/` 下**不再有 implemented 一层**——`implemented/<class>/<file>.md` → `archived/<class>/<file>.md`
2. 在 `Status: implemented` 行**紧下方**插入一行：`Archived: YYYY-MM-DD`
3. 除以上两处外**不改任何内容**（不翻译、不重排、不改标题和标点）
4. 修入站链接：`rg -n "<旧文件名>" .agents`，把引用改成 archived 路径或删除
5. 此后永久冻结：不得编辑，也不得作为现状依据

## 4. 删除场景

- `rejected/` 笔记：其理由已不能防止有意义错误时，连入站链接一起删除
- `implemented/` 笔记：纯机械 / 局部改动本就豁免不写；价值耗尽走归档；只有「纯复述代码事实」才直接删
- `proposed/` 笔记永不归档：过时走拒绝或删除
- 移动 / 删除后必须 `rg` 复查链接

## 5. 收尾自查

- [ ] 新笔记 `Status:` 与所在目录一致，有 `## Problem` 与 `## Alternatives considered`
- [ ] 被完全取代的旧笔记已归档 / 拒绝，入站链接已修
- [ ] `rg` 无指向旧路径的残留引用
