---
name: modality-arch-rules
description: 在 modality 新增或调整 ArchUnit 架构规则时使用，覆盖规则位置、写法风格、负向验证与文档同步。
---

# 架构规则（ArchUnit）

## 位置与运行

- 唯一规则文件：`modality-boot/src/test/java/cn/slainc/boot/ArchitectureTest.java`
- 运行：`./gradlew :modality-boot:test --tests 'cn.slainc.boot.ArchitectureTest'`
- 这是全仓库门禁：改包名、新增模块、调整分层的改动必须跑

## 何时新增

- 新的分层约束（如「某层不得依赖某层」）
- 新的包名 / 命名约束（如禁用词扩展）
- 新模块类别的归属约束

既有分层用 `LAYERED_PACKAGES` 数组 + `layersAbove(...)` 派生「上层集合」；新增库优先登记进数组，而不是写单条散装规则。

## 写法

- `noClasses().that()...should()...` 风格；每条规则一个静态字段 `@ArchTest`，字段名即规则内容（如 `restControllersOnlyInModuleWebPackage`）
- 例外用 `resideOutsideOfPackage` 显式排除，并在命名 / 注释里点名（如 `cn.slainc.web` 库本体豁免）
- 提交信息：`test(arch): 中文一句话`，与实现提交分开

## 负向验证（必须）

新增 / 改动的规则要**先制造一个最小违规确认它会被拒绝**（临时加违规类跑一次，看到失败），再提交。没验证过会拒的规则等于没有规则。

## 文档同步

规则变化同步更新：README 贡献指南「代码规范」、根 `AGENTS.md`「硬性约定」、受影响的 skill（如 [modality-layout](../modality-layout/SKILL.md) 的禁用包名清单）。
