---
name: modality-error-handling
description: 设计或审查 modality 错误语义时使用，覆盖 core 异常体系选型、统一响应 Result 与错误码空间、全局异常处理器边界、全中文文案与防信息泄露默认。
---

# 错误处理

> 手册「异常日志」章（错误码 / 异常处理 / 日志）通用条款见 [java-exception-logging](../java-exception-logging/SKILL.md)；本技能是其在 modality 的落地口径。

## 体系（core 库）

- 异常基类 `AbstractException`（携带 `ErrorCode` + `ErrorType` + 上下文 map），四个派生：
  - `BizException` —— 业务失败（预期内）
  - `ValidationException` —— 参数校验
  - `SystemException` —— 系统缺陷 / 环境问题
  - `ThirdPartyException` —— 外部调用失败
- `ErrorType` 兜底码段：BUSINESS 400100 / VALIDATION 400003 / SYSTEM 500000 / THIRD_PARTY 502000
- 统一响应 `Result<T>(code, message, data, timestamp)`；HTTP 状态由 `Result#httpStatus()` 按 `ResultCode` 映射
- 新错误码：优先复用 `ResultCode` 既有值（400xxx 业务 / 401xxx / 403xxx / 4xx / 500xxx）；确需新增实现 `ErrorCode` 接口，码段跟随分组，message 全中文

## 使用规则

- 业务失败抛 `BizException` 并给**中文、可行动**的消息：说清哪个对象、什么条件不满足，不写「操作失败」这类空话
- 参数校验失败用 `ValidationException`（或 jakarta validation 交给框架归一）
- 不用裸 `RuntimeException` / `IllegalStateException` 表达预期内的业务失败；也别把系统缺陷包装成业务异常吞掉
- 稳定语义（401 / 403 / 409 等）由错误码承载，消费方按码处理，不许解析 message
- `withContext` 只放诊断有用的键值；cause 保留

## 响应与防泄露

- 全局处理在 web 库 `GlobalExceptionHandler`：框架异常映射到对应 `ResultCode`；兜底异常仅在 `modality.web.exception.expose-message=true` 时回显原始 message（生产默认 false）——业务代码不要绕过它手写异常响应
- 安全类异常走 security 库 `SecurityExceptionHandler`（401/403 语义），不要自定义重复
- 用户可见文案与内部诊断分离：日志保留 traceId 与内部细节，响应只给安全信息

## 审查清单

- 预期失败是否有类型、稳定码、可行动中文消息
- 是否误用系统异常表达业务失败 / 误用业务异常吞系统缺陷
- message 是否泄露堆栈、SQL、内部地址、密钥等
