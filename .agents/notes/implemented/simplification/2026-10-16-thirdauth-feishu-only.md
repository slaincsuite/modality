# Agent Note: 第三方登录收敛为仅飞书

Status: implemented

## Problem

第三方登录库（`modality-library-spring-boot-thirdauth`）原先适配微信、企业微信、飞书、钉钉四个平台，四个 provider 与枚举值常驻，可用与否只由 `modality.thirdauth.platform.<code>` 配置决定。产品口径改为只对外提供飞书登录后，其余三个平台既不该被列出，也不该被登录入口接受；而配置层开关在运行时仍可被动态配置打开，产品决定落不到代码上。

## Decision

2026-10-16 起第三方登录只保留飞书：

- `ThirdPartyPlatform` 只保留 `FEISHU`；`WechatAuthProvider` / `WechatWorkAuthProvider` / `DingtalkAuthProvider` 三个实现类删除，`ThirdAuthAutoConfiguration` 不再注册对应 Bean
- 只服务这三个平台的配置项一并删除：`modality.thirdauth.platform.<code>.agent-id`（企业微信）与 `.scene`（微信 / 企业微信的 WEB | SCAN 选择），`JustAuthProvider` 同步去掉 agentId 透传与 `applyScene` 钩子
- 枚举里没有的平台码在认证中心落成 `BizException`（HTTP 400，提示「不支持的第三方平台：…，支持：feishu」），`GET /auth/third/platforms` 只返回飞书

## Alternatives considered

- **只关配置，保留三个 provider 与枚举值**：改动最小，但动态配置能重新打开平台，产品口径可被运行时绕过；枚举里留着永不启用的平台，读代码看不出真实能力边界。放弃。
- **保留类与枚举值，只在自动配置里不注册 Bean**：`/auth/third/platforms` 仍会按配置把平台列出来（列了却登不进去），错误语义含糊，还留下永不注册的死代码。放弃。
- **保留 `agent-id` / `scene` 当通用能力**：只有企业微信、微信用得到，留下即无人消费的死配置；恢复平台时从 git 历史取回更干净。放弃。

## Consequences

- 代价：恢复任一平台要从 git 历史取回 provider、枚举值与配置项；`t_third_account_binding` 里已存在的非飞书平台记录会在读取时失败（按平台码解析枚举），存量部署需先清理这些行
- 收获：平台能力边界与产品口径一致——枚举、自动配置、配置项三处都能直接读出「只有飞书」；旧前端调 `/auth/third/wechat/**` 得到 400 与中文提示，而不是 500

## Testing

- 库单测：`ThirdPartyPlatformTest`（只认飞书，暂停平台码一律拒绝）、`ThirdAuthWiringTest`（只注册飞书 provider），其余 thirdauth 单测全部迁到飞书
- 模块与整机验收：`ThirdPartyLoginModuleTest`、`Batch6ModelAcceptanceTest`、`Batch9AuthAcceptanceTest` 覆盖飞书正向链路与被移除平台码的 400 负向
