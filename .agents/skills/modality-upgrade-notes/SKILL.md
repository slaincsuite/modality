---
name: modality-upgrade-notes
description: 当改动涉及表结构、配置默认值、安全策略或破坏性行为变化时使用，写清升级步骤与回滚方式，并同步 PR 描述与 README。
---

# 升级与回滚说明

## 何时必须写

触发任一即必写（README 贡献指南的硬性要求）：

- 表结构变更（新增列 / 索引、改类型——`IF NOT EXISTS` 模型覆盖不了的）
- 配置默认值变化（`*Properties` 默认值、application.properties）
- 安全策略变化（Cookie、CORS、验证码、算法、暴露面开关）
- 对使用者可见的破坏性行为变化（接口、响应、错误码语义）

## 写在哪

1. **PR 描述**「升级与回滚」段——主载体（模板 [.agents/templates/pr-body.md](../../templates/pr-body.md)）
2. README「升级与备份」或相关章节——面向部署者的步骤变化时同步
3. `.agents/notes/implemented/`——决策依据入笔记；操作步骤不进笔记

## 四件套

- **影响面**：谁受影响（新装 / 存量升级 / 特定配置组合）
- **升级步骤**：人工 DDL（幂等优先）、配置改动、重启要求、验证命令
- **回滚方式**：镜像按 tag 回滚；DDL 可否回滚（加列可留、改类型要写反向语句）；Redis 数据是否需处理
- **验证建议**：升级后跑什么（`/actuator/health`、登录、关键端点冒烟）

## 格式示例

```markdown
### 升级与回滚

**影响面**：存量部署升级需人工 DDL；新装无影响。
**升级步骤**：
1. `ALTER TABLE t_xxx ADD COLUMN ...`（确认幂等或先检查列是否存在）
2. 配置：`modality.xxx.yyy=...`
**回滚**：镜像回滚到 <旧 tag>；DDL 保留新列无副作用 / 需执行 `ALTER TABLE ... DROP COLUMN ...`
**验证**：`curl /actuator/health` + 登录 + 相关端点冒烟
```

## 纪律

- 新表默认幂等（启动自动建），不算破坏性；**变更已有列**才是，必须人工 DDL
- 不许用「见代码」替代；PR 评审按四件套核对
