---
name: modality-branch-merge
description: 在 modality 仓库切换分支、推送、创建 PR 或合并分支时使用，覆盖 main/dev/特性分支模型、推送与 PR 流程（gh 与网页版）、线性历史合并要求、冲突处理与合并前重验。
---

# 分支与合并

## 分支模型

```
main                 稳定分支：只接受来自 dev 的合并
dev                  集成分支：特性分支都合并到这里
feat/xxx、fix/xxx    特性分支：一律从 main 切出
```

- 命名：`feat/tenant-domain-resolver`、`fix/login-crash`（短横线，小写）
- 一个 PR 只解决一件事；PR 标题同样用 `type(scope): 中文一句话`
- 不直接向 `main` / `dev` push 提交，一律走 PR

## 标准流程

```sh
git fetch origin
git switch main && git pull --ff-only origin main
git switch -c feat/<scope>-<topic>

# …开发与提交（见 modality-commit）…

git push -u origin feat/<scope>-<topic>
```

开 PR（二选一）：

```sh
# gh CLI（本机已登录）
gh pr create --base dev --head feat/<scope>-<topic> \
  --title 'feat(scope): 中文一句话' \
  --body '背景 / 改动范围 / 验证方式（命令与结果）'

# 或网页：Pull requests → New pull request，base: dev，compare: 特性分支
```

PR 描述按 `.agents/templates/pr-body.md` 五段式：背景、改动范围、对使用者可见的行为变化、验证方式（命令与结果）、升级与回滚（涉及表结构 / 配置默认值 / 安全策略时必填，细则见 [modality-upgrade-notes](../modality-upgrade-notes/SKILL.md)）。

## 合并

- 保持**线性历史**（仓库当前无 merge 提交）：用 **Rebase and merge** 或 **Squash and merge**，**不要 Create a merge commit**
- 合并前**重跑受影响验证**：审查期间 rebase 过的代码，旧结果不算数
- 合并后删除特性分支（网页版 Delete branch；gh 可 `gh pr merge --delete-branch`）
- `dev → main`：积累一批后开 PR（base `main`，compare `dev`）→ 审批 → 合并

## 与上游对齐 / 冲突

```sh
git fetch origin
git rebase origin/dev        # 特性分支与集成分支对齐
# 解决冲突 → 跑受影响验证 → 推送
git push --force-with-lease
```

- 只对个人特性分支改写历史；`--force-with-lease` 在远端前进时中止，裸 `--force` 禁止
- 冲突不盲解：先确认合入方语义（以谁的行为为准），解完必须重跑受影响 `Batch*AcceptanceTest`
- `dev → main` 的 PR 有冲突时，在 `dev` 一侧解决，不要在 `main` 上改写历史

## 收尾

```sh
git fetch origin
git log --oneline origin/dev -5     # 确认合并结果已在线
git branch -d feat/<scope>-<topic>  # 本地清理（远端通常已由 PR 删除）
```
