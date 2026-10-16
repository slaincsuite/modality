# Agent Note: 第三方扫码登录接出 HTTP 端点

Status: implemented

## Problem

第三方登录库（`modality-library-spring-boot-thirdauth`）已带扫码能力：`beginQrLogin` 生成票据并把平台授权地址当作二维码内容，回调携带 `state=票据号` 时把票据置 `CONFIRMED`，网页端轮询 `pollQr` 取状态。但这套能力此前只被库内测试与 `Batch6` 直接调用，认证中心没有任何 HTTP 入口，前端接不了飞书扫码登录。

## Decision

2026-10-16 在 `modality-modules-auth` 的 `/auth/third` 下接出三个端点（只对已启用平台开放，当前即飞书）：

- `POST /auth/third/{platform}/qr`：创建票据，返回 `ticketId`、二维码内容（平台授权地址）、PNG base64（复用认证中心 `QrImageService`，该服务缺席时为 null）与 `ttlSeconds`
- `GET /auth/third/{platform}/qr/{ticketId}`：轮询状态（`WAITING / SCANNED / CONFIRMED / EXPIRED` + 中文标签 + 过期时间）；票据归属平台不匹配、已过期或不存在一律按 `EXPIRED` 返回
- `POST /auth/third/{platform}/qr/{ticketId}/exchange`：票据属于本平台且为 `CONFIRMED` 时**原子消费**，已绑定直接建会话，未绑定返回 `bindTicketId`（复用 `POST /auth/third/bind` 的密码绑定链路）

库侧配套：`QrTicketStore.consume(ticketId, expected)` 一次拿取（Redis 实现用 Lua 判状态后删除，内存实现用 `remove(key, value)`），`ThirdAuthService.consumeQr` 只消费 `CONFIRMED` 票据并把 payload 解析回登录结果；状态不符时不消费，用户仍可继续完成授权。

没有单独的「刷新二维码」端点：直接再调一次创建即可，旧票据随 TTL 自然失效，避免维护两套失效语义。

## Alternatives considered

- **只接「创建 / 轮询 / 刷新」三件套，把换取会话留给回调**：回调由手机侧浏览器发起，网页端拿不到会话，扫码登录无法落地；刷新又可以被「重新创建」替代。放弃。
- **轮询时直接签发会话（不做 exchange）**：GET 带副作用，且响应丢失就再也拿不回令牌；本仓库自家扫码登录（`/auth/qrcode` 轮询只读 + `/exchange` 单独换取）也是这个形状，保持一致。
- **换取不做一次性消费**：同一张二维码可反复换取会话，一次性凭证变成长期入口，并发换取还会各签一份会话。

## Consequences

- 代价：`/auth/third/**` 新增两处匿名可达写入口（创建票据、换取会话），与自家扫码登录同一暴露面；当前依赖 `modality.web.request-limit` 的通用限流，未单独加验证码
- 收获：前端可以走「网页出码 → 手机飞书授权 → 轮询确认 → 换取会话」完成登录；未绑定用户复用原有密码绑定链路，绑定关系与授权码链路完全共用

## Testing

- 库：`ThirdAuthQrFlowTest`（确认后恰好消费一次、状态不符不消费）、`RedisQrTicketStoreTest`（Redis 侧原子消费语义，真连接）
- 模块：`ThirdPartyLoginModuleTest`（出码 → 未确认不可换 → 回调确认 → 换 bindTicket → 绑定建会话；已绑定直接建会话；未知平台与不存在票据负向）
- 整机：`Batch9AuthAcceptanceTest`（飞书扫码全链路 + 重复换取负向 + 被移除平台的扫码入口 400）
