---
name: modality-new-endpoint
description: 在 modality 业务模块或其 web 包新增 REST 端点时使用，覆盖控制器位置与骨架、权限码与审计注解、公开端点放行评估、租户与身份语义、验收测试登记。
---

# 新增 REST 端点

> 代码风格与命名遵循 [java-coding-standards](../java-coding-standards/SKILL.md)；架构设计遵循 [java-design-standards](../java-design-standards/SKILL.md)。

## 位置与骨架

- 控制器放业务模块 `web/<域>/` 子包；管理端类名 `XxxManageController`，其他按资源语义命名
- `@RestController` + `@RequestMapping("/<module>/<资源>")`；`@Tag(name = "中文域", description = "...")`
- 构造器注入、字段 `private final`；控制器不写业务逻辑
- 请求 / 响应模型放 `dto` 包，用 record（校验注解跟随该模块既有做法，`job` 模块有 jakarta.validation 先例）
- 有重复提交风险的写端点考虑幂等注解（`job` 模块有先例）

## 权限与审计

- 权限码先在模块 `*Keys`（如 `UpmsKeys`）加常量，再 `@RequirePermission(permissions = XKeys.PERMISSION_...)`；控制器与测试都引用常量，不写字面量
- 敏感写操作加 `@AuditLog(value = "中文动作", operation = AuditOperation.CREATE, category = AuditCategory.DATA, module = "<module>")`
- 需要匿名可达的端点：同步评估 boot 的 `modality.security.filter.exclude-patterns`，并确认限流、验证码等防滥用手段

## 租户与身份

- 租户解析走平台分级链（会话 > 域名 > 路径 > 请求头 > 参数）；不许绕开「请求声明租户 vs 会话租户」一致性校验
- 取当前账号一律走 `UserContext`，不自行解析请求头
- 跨租户读写入口走 `TenantCrossAccess`，不要手写 SQL 绕过拦截器

## 测试

1. 模块单测：service 行为 + 控制器合同，包结构镜像 main
2. 端到端：boot 的 `Batch*AcceptanceTest`（`AcceptanceHttpSupport` 发 HTTP；200 / 401 / 403 / 400 正负配对）
3. 跑：

```sh
./gradlew :modality-boot:test --tests 'cn.slainc.boot.BatchXX...'
```

## 收尾

- `feat(scope): ...` 与 `test(scope): ...` 分开提交
- 验证命令与结果按 [modality-pre-push-checks](../modality-pre-push-checks/SKILL.md) 报告
