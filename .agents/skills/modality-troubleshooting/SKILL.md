---
name: modality-troubleshooting
description: 排查 modality 启动失败、接口报错或测试失败时使用，覆盖 403 租户、Redis NOAUTH、验证码 400、dev 与打包差异、测试失败归因与常见 Gradle 问题。
---

# 排障

## 接口层

| 现象 | 原因与处理 |
| --- | --- |
| 所有接口 403「无法识别租户身份」 | `missing-tenant-policy=REJECT` 且未解析出租户：单租户部署设 `ALLOW`（回落平台租户 1），多租户配置域名 / 路径 / 参数解析 |
| 403 租户一致性失败 | 请求声明租户 ≠ 登录会话租户；这是预期防线，不许关校验「修复」 |
| 登录 400「已开启验证码校验」 | 先 `POST /captcha/generate` 拿 captchaId；dev profile 默认关闭验证码 |
| 401 未认证 | 检查 `satoken` 头与 `X-Login-Type` 是否匹配账号体系；会话可能过期（默认 2h 绝对 + 30min 空闲） |
| 前端拿不到 Token Cookie | 生产 Cookie `Secure=true` 必须 HTTPS；或改用响应体 tokenValue 走请求头 |
| 浏览器 CORS 报错 | `modality.web.cors.allowed-origin-patterns` 默认空即不下发头，按前端域名显式配置 |
| NOAUTH / 连不上 Redis | `export MODALITY_REDIS_PASSWORD=...`；Redis 必须开密码 |

## 启动与构建层

| 现象 | 原因与处理 |
| --- | --- |
| dev profile 报 H2 驱动缺失 | H2 是 `developmentOnly`；fat jar 必须提供真实 MySQL |
| 启动因密钥缺失失败 | `modality.jwt.fail-on-missing-key=true` 时未配 JWT 密钥会快速失败，属预期 |
| Gradle 结果怪异 | `./gradlew clean build --no-build-cache` 复核，先排除增量缓存干扰 |
| 单测全绿但整机验收红 | 多为装配 / 时序问题（`@AutoConfigureBefore/After`、MapperFactoryBean 时序、条件注解）；跑 `./gradlew :modality-boot:test` 定位 |

## 测试失败归因流程

1. 单类复现：`./gradlew :<module-path>:test --tests '<类全名>'`，先确认选中用例数
2. 读中文失败信息（断言消息全中文，通常直接指出语义）
3. 环境类怀疑（Redis 密码、端口占用）：先用 redis-cli / 最小脚本验证连通性，再归因
4. 并行相关（Gradle `parallel=true`）：检查该模块 Redis database 编号是否与其他模块撞了
5. flaky / 时间敏感性：先核对时钟与余量，参照历史修复方式（MFA 用例曾放宽时间余量）
6. 本地与 CI 不一致：比对 JDK 版本（21）、环境变量、时区

## 纪律

- 先证明可复现、找到根因，再改代码或测试；不许用放宽断言 / 跳过用例消灭红色
- 无法本地复现的偶发失败：记录命令、失败用例、环境，交用户决策
