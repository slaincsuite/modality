---
name: modality-code-review
description: 审查 modality 仓库改动或自审时使用，按租户安全、权限审计、分层包名、自动配置、安全默认、文案与测试惯例逐项检查，findings 优先输出。
---

# modality 代码审查

findings 优先：先按严重级列问题（带 `文件:行`），再谈总评；没有 finding 就直说，并列出残余风险。重点不是风格洁癖，而是行为回归、越权与装配缺口。

## 检查清单（按风险排序）

1. **租户安全 fail-closed**：新表是平台级还是租户级？平台级必须登记租户忽略集（`TenantIgnoreTables.merge`，模式见 `UpmsTableWhitelist`）；租户级必须有 `tenant_id`；`X-Tenant-Id` 头解析不得被默认打开
2. **权限与审计**：管理端点有没有 `@RequirePermission`；权限码是否走模块 `*Keys` 常量（禁止字面量）；敏感写操作有没有 `@AuditLog`
3. **分层与包名**（ArchUnit 会拒）：库内禁用模块独占包名；控制器只在业务模块 / 业务服务 `web` 包；库不注册端点；业务模块之间零依赖
4. **自动配置完备性**：`@ConditionalOnMissingBean`、`@ConditionalOnProperty(matchIfMissing = true)`、`compileOnly` 配 `@ConditionalOnClass(name = "...")`、`AutoConfiguration.imports` 是否登记
5. **安全默认**：CORS 空白名单、Cookie `Secure`、异常不回显原始 message、弱算法默认禁用；dev-only 的放宽有没有漏进主配置
6. **依赖**：版本只在 `libs.versions.toml`；能 `implementation` 不 `api`；新增 `compileOnly` 时测试侧同步
7. **文案与注释**：异常 / 日志 / 断言全中文；注释只 `//` 且只在反直觉处；配置文件零注释
8. **测试**：包结构镜像 main；行为变化同 PR 改测试；跨模块行为进 boot 的 `Batch*AcceptanceTest`；高危面（租户、鉴权、签名、上传下载、SSRF、限流）有 401/403/400 负向对照
9. **DDL**：幂等、与实体字段对齐、H2（`MODE=MySQL`）与 MySQL 8 双兼容、约束命名跟随既有
10. **事务与结构约束**：service 层事务边界；关联写同事务；树形结构有环校验之类的不变量
11. **SPI 边界**：跨模块能力是否由「接口 + 模型」承载；有没有模块间偷 import

## 输出格式

- 每条 finding：严重级 + `文件:行` + 为什么是问题 + 建议方向
- 不重写实现、不做与仓库约定无关的评论
- 结尾列残余风险：没跑的验证、没覆盖的边界

## 参照

- 架构规则原文：`modality-boot/src/test/java/cn/slainc/boot/ArchitectureTest.java`
- 硬性约定：根 `AGENTS.md`
- 开发规范标准技能：[java-security-standards](../java-security-standards/SKILL.md)（安全条款）/ [java-coding-standards](../java-coding-standards/SKILL.md)（编程规约）
