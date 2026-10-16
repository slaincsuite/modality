## 背景

（解决什么问题、来源 Issue；无则写「无」）

## 改动范围

（涉及模块与关键文件；一个 PR 只解决一件事）

## 对使用者可见的行为变化

（接口、配置默认值、错误码语义等；无则写「无」）

## 验证方式（命令与结果）

```text
export MODALITY_REDIS_PASSWORD=...
./gradlew clean build --no-build-cache

（粘贴实际运行的命令与输出摘要）
```

## 升级与回滚（涉及表结构 / 配置默认值 / 安全策略时必填）

（影响面 / 升级步骤含人工 DDL / 回滚方式 / 验证建议；细则见 .agents/skills/modality-upgrade-notes/SKILL.md）
