---
name: modality-find-simplifications
description: 在 modality 扫描简化机会时使用，按仓库约定找死代码、重复注册、过度包装与可收敛结构，输出 findings 且不改行为。
---

# 找简化项

目标：在不改变可观察行为的前提下删代码、删配置、收敛结构。输出 findings，由用户决定执行哪些——这是扫描任务，不是必须落地。

## 扫描方向（按收益排序）

1. **重复与失效注册**：`AutoConfiguration.imports` 重复项；注册了但无人引用的 Bean / 常量；占位符式常量
2. **死代码**：无调用链的类 / 方法（rg 搜引用）；被新实现替代但未删除的旧路径；只为测试存在的 production 代码
3. **过度包装**：只有一行的委托类；无多态意图的单实现接口；把 record 包成 builder 的样板
4. **可收敛结构**：同名概念多处定义；库内可合并的包（历史先例：合并接口与管理实现包、清理与业务模块重名的包、表名常量收敛至实体）
5. **冗余配置**：与默认值相同的显式配置；被注释掉的配置项（仓库约定配置文件零注释）

## 方法

- 引用搜索：`rg -n "类名|常量名" -g '!build'`
- 分层 / 命名线索直接对照 `ArchitectureTest` 与 [modality-layout](../modality-layout/SKILL.md)
- 覆盖率报告（`build/**/reports/jacoco`）辅助找未执行代码——「未覆盖」不等于「死」

## 输出格式

每项给出：位置（`文件:行`）、证据（引用搜索结果）、删除风险、建议提交信息（`refactor(scope): ...`）。

## 纪律

- **只做删除 / 收敛，不做行为变更**；发现行为问题走 bug 流程，不顺带改
- 删除库对外类属破坏性变更，按 [modality-upgrade-notes](../modality-upgrade-notes/SKILL.md) 处理
