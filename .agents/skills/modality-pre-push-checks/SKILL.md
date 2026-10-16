---
name: modality-pre-push-checks
description: 在 modality 仓库提交、推送、宣布完成或排查验证失败前使用，按改动面选择覆盖它所需的最小验证集合，避免反射式跑全量测试，也不放过影响装配与架构的改动。
---

# modality 提交前检查

推送前先能回答「改了什么 → 跑了什么 → 结果如何」，只报告实际运行过的命令。

## 环境前提

```sh
export MODALITY_REDIS_PASSWORD=<本机 Redis 密码>
```

Redis 无密码会 NOAUTH 直接失败；真连接用例连 `127.0.0.1:6379`。

## 按改动面选证据

| 改动面 | 最小证据 |
| --- | --- |
| 单个库 / 模块内部类 | 该模块 `./gradlew :<module-path>:test` |
| 公共行为、自动装配、SPI 接线 | 受影响 `Batch*AcceptanceTest` 或全部 `./gradlew :modality-boot:test` |
| 包名、分层、控制器位置、新增模块 | `./gradlew :modality-boot:test --tests 'cn.slainc.boot.ArchitectureTest'` |
| 新增依赖 | 先改 `gradle/libs.versions.toml`，再跑 owning 模块测试；锁文件不入库 |
| DDL / schema / 实体 | 受影响模块测试（dev 走 H2）+ 涉及的 Batch 验收 |
| 安全过滤器、登录 / 鉴权链路 | 对应 Batch 验收（Batch4 / 8 / 9 等） |
| 配置默认值 | 读该 `*Properties` 的模块测试 + boot 验收对照 |
| 纯文档、注释 | 无自动化验证；核对相对链接与命令真实可执行 |

`<module-path>` 例：`:modality-library:modality-library-spring-boot-tenant`、`:modality-modules:modality-modules-upms`。

## 单测试类 / 单方法

```sh
./gradlew :modality-modules:modality-modules-upms:test --tests 'cn.slainc.modules.upms.<包>.<类名>'
```

过滤后先看输出里的「选中用例数」，为 0 说明过滤写错，不要当成通过。

## 收工必须全量

本仓库没有 CI 兜底，README 是权威要求：推送 / 宣布完成前跑通

```sh
./gradlew clean build --no-build-cache
```

迭代期间可以窄，收工必须全量——不允许「只跑了一小撮」就宣布完成；也不允许为了变绿而降覆盖率阈值、跳过用例或放宽断言。

## 失败处理

- 把失败归为环境问题前，给出确定性证据（命令、失败用例、环境差异点）
- 先修失败再推送，不赌「CI 会绿」（本仓库也没有 CI 可赌）
- 报告时说清楚：跑了什么、哪条失败、什么没跑

## 收尾清单

1. 每条改动都有对应证据（命令 + 结果）
2. 未跑的检查如实列为未跑
3. 提交信息 `type(scope): 中文一句话`；实现与测试分开提交
