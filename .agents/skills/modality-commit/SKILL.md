---
name: modality-commit
description: 在 modality 仓库生成提交、拆分提交粒度或自查提交内容时使用，覆盖提交消息格式、实现与测试分离、提交前状态检查与不入库文件清单。
---

# modality 提交规范

## 提交前状态检查

```sh
git status --short --branch
git diff --cached --stat
```

- 确认在 `feat/xxx` / `fix/xxx` 特性分支上；`main` / `dev` 不接受直接提交
- 确认 stage 里没有意外文件：**`.idea/`、`build/`、`*.log`、`.apifox`、`.env*` 不入库**（`.gitignore` 已覆盖，仍以 `git status` 复核）；`AGENTS.md` 与 `.agents/` 已入库，改动照常提交，但禁止夹带凭据与个人信息
- 本仓库没有 git hooks，格式与范围全靠自查

## 消息格式（硬性）

单行标题、不写正文：`type(scope): 中文一句话`

- `type`：`feat` / `fix` / `test` / `refactor` / `docs` / `chore`
- `scope`：模块短名（`tenant` / `security` / `upms` / `boot` / `build` / `readme` 等）
- 中文一句话说清做了什么，动词开头（新增 / 修复 / 补齐 / 收敛 / 移除 / 联动更新）
- 不带句号、不带 emoji、不写日期、不写「详见」类指代

真实示例（仓库历史）：

```
feat(tenant): 新增按域名解析租户并补一致性校验
fix(storage): 修复读路径绕过装饰链导致加密文件下载为密文
test(upms): 补角色成环校验用例
refactor(tenant): 表名常量收敛至实体并移除冗余声明
```

## 粒度

- 按「可独立描述的一个能力面」拆提交
- **实现与测试分开提交**（`feat(x): ...` 与 `test(x): ...` 两条），各自能独立描述
- 不混无关改动：顺手发现的其他问题单独成条，或先与用户确认

## 历史操作

- 仓库历史线性（无 merge 提交），保持这个形状
- 不 amend 已推送的提交；未推送的本地提交可自由整理
- 需要改写已推送的个人特性分支：只允许 `git push --force-with-lease`，**禁止裸 `--force`**

## 提交后自查

```sh
git show --stat HEAD
git log --oneline -5
```

message 符合格式、stat 无意外文件；提交验证按 [modality-pre-push-checks](../modality-pre-push-checks/SKILL.md) 执行。
