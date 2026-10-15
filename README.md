# modality

> 面向企业多租户场景的 Spring Boot 基础平台：34 个可独立引用的自动配置库（含工单引擎）+ 9 个业务模块（含工单中心、租户中心与定时任务中心）+ 1 个业务服务模块（直播监控）+ 1 个启动模块。

modality 把企业后台里反复要写的那一层沉淀成**可复用、可关闭、可覆盖**的库：多租户数据隔离、七套账号体系的认证鉴权、用户权限中心、OAuth2 / SSO / API Key / 签名协议、审计与策略引擎、文件存储、通知、动态配置、日志与监控。业务模块只负责端点与编排，库模块只提供能力与服务，二者用 SPI 解耦；启动模块负责聚合。

- **运行环境**：JDK 21、MySQL 8.0+、Redis 6+
- **技术栈**：Spring Boot 4.1.1 / Spring 7 / Jackson 3、Sa-Token 1.46.0、MyBatis-Plus 3.5.17、dynamic-datasource 4.5.0、Redisson 4.8.0
- **构建**：Gradle 9.8.0（wrapper 锁定），全量构建与测试 3100+ 用例
- **许可证**：GPL-3.0

## 目录

- [功能特点](#功能特点)
- [模块清单](#模块清单)
- [快速开始（本地开发）](#快速开始本地开发)
- [部署指南（Docker）](#部署指南docker)
- [使用示例](#使用示例)
- [配置速查](#配置速查)
- [贡献指南](#贡献指南)
- [许可证](#许可证)

## 功能特点

### 多租户与数据隔离

- **行级隔离默认开启**：MyBatis-Plus 租户拦截器按 `tenant_id` 自动改写 SQL，无 `tenant_id` 的表必须进白名单，未登记即报错（fail-closed）
- **租户上下文来源可配且分级可信**：登录会话（最高优先级）→ 域名 → 路径 → 请求头 → 参数；`X-Tenant-Id` 头解析器默认关闭
- **一致性校验**：鉴权完成后校验「请求声明的租户」与「登录会话租户」，不一致直接 403，客户端无法用请求头越权切租户
- 支持行级 / Schema 级 / 独立数据库三种隔离策略，含租户生命周期（开通、试用、续期、冻结、注销）与配额；租户生命周期与配额查询端点由租户中心（`/tenant/**`，权限码 `tenant:manage:*`）提供
- 平台级表（审计、动态配置、数据源等）通过白名单合并机制统一登记，合并后自动刷新拦截器
- **租户套餐**：额度（用户数 / 存储 / 单文件 / 导出条数 / 并发导出 / 限流）、日志保留天数、功能开关、数据权限范围的模板化管理；绑定即生效（60 秒进程内缓存），未配维度回落全局默认（支持动态配置覆盖）

### 认证与安全

- **七套账号体系**：平台管理员 / 平台用户 / 租户管理员 / 租户用户 / 应用 / 服务 / 访客，各自独立令牌、独立会话、互不干扰，踢人只影响本体系
- 登录安全：验证码（默认开启）、失败锁定、封禁、异地登录告警、IP / 时段 / 设备登录限制（**密码、扫码、第三方回调、绑定四条链路同样裁决**）
- 会话安全：默认 2 小时绝对有效期 + 30 分钟空闲超时，可按体系配置；Cookie `HttpOnly` + `Secure` + `SameSite`
- 令牌安全：JWT 支持 HS256 / RS256 / ES256、密钥轮换、JWKS 发布；吊销前强制验签，吊销键 TTL 钳制，`introspect` 按 RFC 7662 要求客户端鉴权
- 口令存储：BCrypt（cost 10）；字段级加密支持 SM4 / AES，等值查询自动加密参数
- 传输与响应：默认 CSP、`X-Content-Type-Options: nosniff`、兜底异常不回显原始 message、CORS 默认空白名单
- 弱算法（DES / ECB）默认禁用，需显式开启才能使用
- **MFA / TOTP 二次验证**：RFC 6238 动态口令（自研实现无第三方依赖）、恢复码、失败锁定；开启后登录返回挑战而不是令牌，`POST /auth/mfa/verify` 换取正式登录态，仅对四类用户账号体系生效

### 用户权限中心（UPMS）

用户、角色、权限、部门、用户组、菜单、数据权限、访问授权、系统注册表、操作日志、登录记录、用户统计；树形结构带成环校验，关联写全部在事务内，软删自动释放用户名唯一约束。账号自助数据：我的收藏、最近访问、热门菜单推荐（按最近 30 天访问次数，默认 8 条），均只作用于当前登录账号。

### 开放平台与协议

- **OAuth2**：授权码 / 隐式 / 密码 / 客户端凭证 / 刷新令牌五种模式，PKCE，同意书管理，客户端密钥恒定时间比较 + PBKDF2 派生
- **SSO**：票据登录与全端单点登出；未配置服务端密钥时自动禁用服务端装配（避免空密钥签名）
- **API Key**：可配前缀、TTL、QPS 限流、IP 白名单
- **请求签名**：HMAC / 证书签名，nonce 验签通过后才消费（签名错误的合法重试不会被判重放），高敏应用禁止降级到单机存储
- **第三方登录**：JustAuth 引擎（钉钉、飞书、企业微信等），外呼带超时，state 一次性消费，绑定前先验密码

### 工单中心

- **引擎库与业务模块分层**：`workorder` 引擎库（纯 Java、零存储依赖）承载状态机 / 编号 / SLA / 通知等领域能力，`modules-workorder` 提供 HTTP 端点与 MySQL 落库，其他应用可只引引擎库复用能力
- **可配置状态机**：状态与流转按类型定义，前置条件支持 SpEL（`#order` / `#vars` / `#now`），前 / 后置钩子 SPI；内置待处理 → 处理中 → 已解决 → 已关闭主链路，含挂起 / 恢复 / 重开 / 转派 / 升级 / 认领
- **工单编号**：前缀 + 日期 + 序号，按天 / 按月重置可配，序号存储 SPI 化
- **模板与自定义字段**：11 种字段类型，必填 / 长度 / 范围 / 正则 / 选项校验，模板版本化与回滚
- **SLA 与工作日历**：响应 / 解决时限、预警百分比、工作日 / 节假日 / 调休精确计算
- **协作与触达**：评论（内外部可见性、楼中楼、点赞）、附件（复用统一存储）、订阅、站内信通知（事件驱动、去重、失败重试）
- **分配策略 SPI**：内置轮询与最少在办，可注册自定义策略
- **统计与导出**：总览 / 分布 / 处理人排行 / 平均处理时长，Excel 导出（复用统一导出组件）

### 审计与策略

- 审计中心：登录日志、操作日志、API 访问日志、策略评估日志，统计全部 SQL 聚合下推，导出限流限长
- 策略引擎：基于属性的访问控制，deny 优先、无 allow 即默认拒；支持仿真推演、版本管理、导入导出、测试用例
- 告警与通知：站内信 + Webhook（钉钉 / 飞书 / 企业微信 / 自定义），Webhook 地址做 SSRF 校验，失败退避重试
- **字段级变更审计**：`@ChangeAudited(type, id=SpEL)` 切面采集 before/after，JaVers 逐字段差异（内置回退引擎）、敏感字段自动掩码、事务提交后异步落库（`ChangeRecorder` SPI）
- **定时任务中心**：Quartz 内核（cron / misfire / 重试退避）+ 任务定义、手动触发、执行日志、节点注册管理端点；分布式锁用平台 Redisson，集群存储表自动建表

### 基础设施

- 存储：本地 / S3 / MinIO 三后端统一契约，分片上传与断点续传、秒传（引用表持久化，重启不丢）、回收站、签名 URL、可选病毒扫描、图片 EXIF 按需读取
- 动态配置：写入即广播、支持加密存储、历史回滚、导入导出、跨节点一致
- 缓存：两级缓存（L1 本地 + L2 Redis），按调用方 TTL 生效，支持跨节点失效广播
- 日志：控制台查询、级别热调整、按大小与窗口下载；SQL 慢日志异步采集且保留 traceId
- 监控：JVM / 系统 / 数据源 / Redis / 会话指标，Actuator 自定义端点
- **SSE 推送**：连接注册、心跳、断线事件重放（Last-Event-ID）、Redis 集群广播；认证器可插拔
- **搜索引擎**：Lucene 内嵌倒排索引、中文二元分词、BM25 打分、短语 / 排除语法、命中高亮；文档源走 Mapper 复用租户行级过滤，支持重建索引与按账号的搜索历史
- **通用回收站**：删除前写 JSON 快照，支持恢复与彻底删除、过期自动清理；可回收类型注册表 + 存储 SPI（infra 提供 JDBC 落库与公告回收示范）
- **Webhook 事件订阅**：开放平台订阅管理 + HMAC-SHA256 签名投递（`X-Modality-Signature`）、指数退避重试、投递记录与手动重投、多节点 Redisson 锁去重

### 工程化

- 依赖与版本由 BOM 统一收口，模块间依赖按层分向，ArchUnit 固化分层规则
- 每个库都可在使用方用 `@ConditionalOnMissingBean` 覆盖，每项能力都能用 `@ConditionalOnProperty` 单独关闭
- JaCoCo 覆盖率报告、CycloneDX SBOM、许可证报告、OWASP 依赖漏洞扫描（侧车脚本，默认不影响构建）

## 模块清单

### 库模块（33）

| 分层 | 模块 |
| --- | --- |
| 基础 | `utils`、`crypto`、`core`、`web`、`excel` |
| 数据 | `datasource`、`mybatis`、`redis`、`tenant` |
| 能力 | `idempotent`、`storage`、`logger`、`monitor`、`sensitive`、`recycle`、`search`、`sse` |
| 安全与协议 | `security`、`jwt`、`oauth2`、`sso`、`api-key`、`captcha`、`sign`、`session`、`mfa` |
| 领域 | `open-api`、`policy`、`thirdauth`、`workorder`、`webhook`、`job`、`change-audit` |
| 平台 | `bom` |

### 业务模块（9）

`upms`（用户权限）、`auth`（认证中心）、`open`（开放平台）、`audit`（审计中心）、`policy`（策略管理中心）、`infra`（基础设施中心）、`workorder`（工单中心）、`tenant`（租户中心）、`job`（定时任务中心）

### 业务服务模块（1）

`live-monitor`（直播监控）：与业务模块同层的独立服务单元，包根 `cn.slainc.services.livemonitor`，配置前缀 `modality.live-monitor`；当前仅搭好装配骨架，端点与服务随业务实现补齐。

### 启动模块（1）

`modality-boot`：聚合全部库与业务模块，只做装配与配置，不含功能性端点。

## 快速开始（本地开发）

### 前置条件

| 依赖 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 21 | 由 Gradle toolchain 锁定，无需改 `JAVA_HOME` 之外的设置 |
| Redis | 6+ | **必须设置密码**，通过环境变量 `MODALITY_REDIS_PASSWORD` 注入 |
| MySQL | 8.0+ | 仅生产需要；`dev` profile 使用 H2 内存库 |

### 启动

```bash
git clone <repository-url> modality
cd modality

# 本机 Redis 有密码时必须导出，否则会以“无密码”连接而失败
export MODALITY_REDIS_PASSWORD=<你的 Redis 密码>

./gradlew :modality-boot:bootRun
```

`bootRun` 默认激活 `dev` profile：H2 内存库 + `127.0.0.1:6379` 的 Redis，无需 MySQL 即可跑起来。

```bash
curl -s http://localhost:8080/actuator/health
# {"status":"UP"}
```

> `dev` profile 为了本地调试放宽了若干限制（验证码关闭、`/actuator/**` 匿名、接口文档开启、Cookie 不强制 Secure）。**生产环境不要使用 dev profile**。

### 构建与测试

```bash
./gradlew clean build --no-build-cache          # 全量构建 + 全部测试
./gradlew :modality-library:modality-library-spring-boot-tenant:test   # 单模块测试
./gradlew build -PcoverageGate                  # 启用 80% 覆盖率门槛
./gradlew :modality-boot:bootJar                # 产出可执行 fat jar
```

跑测试前同样需要 `export MODALITY_REDIS_PASSWORD=<密码>`：需要真实 Redis 的用例会连接 `127.0.0.1:6379` 的对应 database。

## 部署指南（Docker）

### 前置条件

- Docker 24+ 与 Docker Compose v2
- 至少 2 vCPU / 4 GB 内存（构建镜像时建议 4 GB 以上）
- 生产环境请准备域名与 TLS 证书（Cookie 默认强制 `Secure`）

### 方式一：docker compose 一键起（应用 + MySQL + Redis）

仓库根目录已提供 `Dockerfile`、`docker-compose.yml`、`.env.example`。

```bash
cp .env.example .env
vim .env          # 至少改掉三个密码
```

`.env` 必填项：

| 变量 | 说明 |
| --- | --- |
| `MYSQL_ROOT_PASSWORD` | MySQL root 口令 |
| `MYSQL_PASSWORD` | 应用连 MySQL 的口令 |
| `MODALITY_REDIS_PASSWORD` | Redis 口令（平台强制要求非空） |

启动：

```bash
docker compose up -d --build
docker compose ps
docker compose logs -f app
```

首次启动时各业务模块会自动执行 `CREATE TABLE IF NOT EXISTS` 建表脚本（幂等），无需手工导入 DDL。

### 创建首个管理员

平台没有内置默认账号（避免弱口令后门），首次部署需播种一个管理员。仓库提供了幂等脚本：

```bash
# 等待应用启动完成、建表结束后执行
set -a && . ./.env && set +a
docker compose exec -T mysql \
  mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" < deploy/seed-admin.sql
```

脚本会创建：

- 管理员账号 `admin` / `Admin@12345`（口令哈希由本项目 `BCryptUtils` 生成，`must_change_password=1`）
- 角色 `platform-admin`（数据范围 `ALL`），并绑定 `admin`
- 平台管理所需的 50 个 `upms:*` 权限码，并全部授予该角色

> **登录后第一件事：修改 `admin` 的口令。** 该账号带 `must_change_password` 标记，登录响应里的 `data.mustChangePassword` 为 `true`，前端应据此强制跳转改密；改密接口为 `POST /auth/password/change`。
>
> 脚本可重复执行（`INSERT IGNORE`），库中已存在同名账号时不会覆盖。

### 方式二：只部署应用镜像，外接 MySQL / Redis（推荐生产）

```bash
docker build -t registry.example.com/modality:1.0.0 .

docker run -d --name modality \
  -p 8080:8080 \
  -e TZ=Asia/Shanghai \
  -e SPRING_DATASOURCE_DYNAMIC_PRIMARY=master \
  -e SPRING_DATASOURCE_DYNAMIC_DATASOURCE_MASTER_DRIVER_CLASS_NAME=com.mysql.cj.jdbc.Driver \
  -e SPRING_DATASOURCE_DYNAMIC_DATASOURCE_MASTER_URL='jdbc:mysql://mysql.example.com:3306/modality?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false' \
  -e SPRING_DATASOURCE_DYNAMIC_DATASOURCE_MASTER_USERNAME=modality \
  -e SPRING_DATASOURCE_DYNAMIC_DATASOURCE_MASTER_PASSWORD='<MySQL 口令>' \
  -e SPRING_DATA_REDIS_HOST=redis.example.com \
  -e SPRING_DATA_REDIS_PORT=6379 \
  -e SPRING_DATA_REDIS_PASSWORD='<Redis 口令>' \
  -e MODALITY_REDIS_PASSWORD='<Redis 口令>' \
  -e MODALITY_TENANT_STATUS_CHECK_MISSING_TENANT_POLICY=ALLOW \
  -e MODALITY_CORS_ALLOWED_ORIGINS='https://admin.example.com' \
  registry.example.com/modality:1.0.0
```

挂载外部配置文件（推荐，便于版本化管理）：

```bash
docker run -d --name modality \
  -p 8080:8080 \
  -v /etc/modality/application.properties:/app/config/application.properties:ro \
  -e SPRING_CONFIG_ADDITIONAL_LOCATION=file:/app/config/application.properties \
  registry.example.com/modality:1.0.0
```

### 数据库与缓存要求

**MySQL**

- 版本 8.0+，库与连接统一 `utf8mb4`
- 建库：`CREATE DATABASE modality DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;`
- 建表：应用启动自动执行（DDL 集中在仓库根目录 `db/` 下，按模块命名的 `*-schema.sql`），可用 `modality.<module>.auto-create-table=false` 关闭后改为人工 DDL
- 账号权限：`SELECT, INSERT, UPDATE, DELETE, CREATE, INDEX, ALTER, REFERENCES`

**Redis**

- 版本 6+，**必须开启密码**（`requirepass`），平台默认按有密码方式连接
- 单实例即可；生产建议开启 AOF 持久化与主从
- 会话、缓存、限流、幂等、吊销名单都落在 Redis 上，Redis 不可用会直接影响登录与鉴权

### 生产必读配置

| 配置项 | 默认值 | 生产建议 |
| --- | --- | --- |
| `modality.tenant.status-check.missing-tenant-policy` | `REJECT` | **单租户部署设为 `ALLOW`**（回落平台租户 1）；多租户按域名 / 路径 / 参数解析租户后保持 `REJECT` |
| `modality.tenant.resolver.header.enabled` | `false` | 仅在前端可信场景开启 `X-Tenant-Id`；开启后仍会与登录会话租户做一致性校验 |
| `modality.tenant.resolver.domain.root-domain` | 空 | 多租户 SaaS 建议配置根域名，按子域解析租户 |
| `modality.web.cors.allowed-origin-patterns` | 空 | 配置前端实际域名，例如 `https://admin.example.com`；留空即不下发 CORS 授权头 |
| `modality.web.cors.allow-credentials` | `false` | 确需携带 Cookie 跨域时改为 `true`，此时**不能**使用通配源 |
| `modality.security.cookie.secure` | `true` | 全站 HTTPS 时保持 `true`；纯 HTTP 内网可临时改 `false` |
| `modality.security.session.timeout-*` | `7200` | 按账号体系收紧（管理端建议 2 小时以内） |
| `modality.jwt.default-algorithm` | 项目内为 `RS256` | 生产使用非对称算法，并配置密钥轮换 |
| `modality.jwt.fail-on-missing-key` | `false` | 建议改 `true`，未配置密钥时直接启动失败而不是告警放行 |
| `modality.sso.server.secret-key` | 空 | 使用 SSO 时必填；留空会整体禁用 SSO 服务端装配 |
| `modality.datasource.encrypt.enabled` / `key` | `false` / 空 | 动态数据源密码落库前加密，生产建议开启并妥善保管 KEK |
| `management.endpoints.web.exposure.include` | `health,info` | 不要放开 `*`；Prometheus 抓取如需 `prometheus`，请配合内网访问或独立鉴权 |
| `modality.web.open-api.enabled` | `false` | 生产保持关闭 |
| `modality.auth.captcha.enabled` | `true` | 保持开启，避免撞库 |
| `MODALITY_TIME_ZONE` / `TZ` | `Asia/Shanghai` | 与数据库 `serverTimezone` 保持一致 |

### 反向代理与 HTTPS

应用默认监听 8080，建议由 Nginx 终止 TLS：

```nginx
server {
    listen 443 ssl http2;
    server_name api.example.com;

    ssl_certificate     /etc/nginx/certs/api.example.com.crt;
    ssl_certificate_key /etc/nginx/certs/api.example.com.key;

    client_max_body_size 210m;   # 与 spring.servlet.multipart.max-request-size 对齐

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 120s;
    }
}
```

平台会读取 `X-Forwarded-For` / `X-Real-IP` 还原客户端 IP，登录限制与审计日志依赖它。

### 升级与备份

- **升级**：`docker compose pull`（或重新 `build`）→ `docker compose up -d`。表结构脚本是 `IF NOT EXISTS` 语义，新增列/索引需按发布说明人工执行 DDL
- **备份**：MySQL 用 `mysqldump`（含 `t_user`、`t_audit_*` 等）；Redis 数据（会话、缓存、限流）可视作可重建，但吊销名单丢失会导致已吊销令牌复活，建议同时备份 `appendonly.aof`
- **回滚**：镜像按 tag 回滚；涉及表结构变更的版本请先在预发验证

### 常见问题

| 现象 | 原因与处理 |
| --- | --- |
| 所有接口返回 403「无法识别租户身份」 | `missing-tenant-policy=REJECT` 且请求未解析出租户。单租户部署改为 `ALLOW`，或配置域名/路径/参数解析器 |
| 启动报 `NOAUTH Authentication required` | Redis 需要密码但未提供：设置 `MODALITY_REDIS_PASSWORD`（compose 中该变量同时驱动 Redis 与应用的密码） |
| 用打包产物 + `dev` profile 启动失败 | H2 是 `developmentOnly` 依赖，**不在 fat jar 里**；jar 启动必须提供真实 MySQL |
| 登录返回 400「已开启验证码校验」 | 生产默认开启验证码，需先调 `POST /captcha/generate` 拿 `id`，再带 `captchaId`/`captchaCode` 登录 |
| 跨域请求浏览器报 CORS 错误 | `modality.web.cors.allowed-origin-patterns` 为空即不下发 CORS 头，按前端域名显式配置 |
| 前端拿不到 Token Cookie | 生产 `modality.security.cookie.secure=true`，必须走 HTTPS；或改用返回体里的 `tokenValue` 走请求头 |

## 使用示例

所有业务接口返回统一报文：

```json
{ "code": 200, "message": "成功", "data": {}, "timestamp": 1791868741523 }
```

账号体系通过请求头 `X-Login-Type` 指定（`PLATFORM_ADMIN` / `PLATFORM_USER` / `TENANT_ADMIN` / `TENANT_USER` / `APP` / `SERVICE` / `GUEST`），缺省为 `TENANT_USER`。

### 1. 获取验证码并登录

```bash
# 取验证码（生产默认开启；dev profile 关闭）
curl -s -X POST http://localhost:8080/captcha/generate \
  -H 'Content-Type: application/json' \
  -d '{"channel":"WEB"}'
# => data.id 即 captchaId，data.imageBase64 为图片

# 登录
curl -s -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -H 'X-Login-Type: TENANT_USER' \
  -d '{
        "username": "admin",
        "password": "Admin@12345",
        "captchaId": "<captchaId>",
        "captchaCode": "<答案>",
        "deviceType": "WEB"
      }'
# => data.tokenName = "satoken"，data.tokenValue 即令牌
```

### 2. 携带令牌访问业务接口

Sa-Token 默认从 `satoken` 请求头（或同名 Cookie）读取令牌：

```bash
TOKEN=<上一步的 tokenValue>

curl -s 'http://localhost:8080/upms/user/page?current=1&size=20' \
  -H "satoken: $TOKEN" \
  -H 'X-Login-Type: TENANT_USER'
```

### 3. 令牌续期 / 吊销 / 校验

```bash
# 刷新（匿名可达）
curl -s -X POST http://localhost:8080/auth/token/refresh \
  -H 'Content-Type: application/json' \
  -d '{"token":"<refreshToken>"}'

# 校验（需要登录会话 + 客户端鉴权，符合 RFC 7662）
curl -s -X POST http://localhost:8080/auth/token/introspect \
  -H "satoken: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"token":"<accessToken>","clientId":"<clientId>","clientSecret":"<clientSecret>"}'

# 吊销（需要登录会话；服务端会先验签再吊销）
curl -s -X POST http://localhost:8080/auth/token/revoke \
  -H "satoken: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"token":"<accessToken>"}'

# 登出
curl -s -X POST http://localhost:8080/auth/logout -H "satoken: $TOKEN"
```

### 4. 多租户调用

平台租户 ID 为 `1`。三种传租户的方式按可信度排序：

```bash
# (a) 参数（默认开启，适合内部调用）
curl -s 'http://localhost:8080/upms/user/page?tenantId=2' -H "satoken: $TOKEN"

# (b) 请求头（默认关闭，需显式开启；开启后仍与登录会话租户做一致性校验）
#     配置：modality.tenant.resolver.header.enabled=true
curl -s http://localhost:8080/upms/user/page -H "satoken: $TOKEN" -H 'X-Tenant-Id: 2'

# (c) 域名（多租户 SaaS 推荐）
#     配置：modality.tenant.resolver.domain.root-domain=example.com
curl -s https://acme.example.com/upms/user/page -H "satoken: $TOKEN"
```

> 请求声明的租户与登录会话租户不一致时返回 403，客户端无法通过改请求头读到别的租户数据。

### 5. 开放平台

```bash
# 客户端凭证模式换取令牌（客户端需在开放平台注册并授权 client_credentials）
curl -s -X POST http://localhost:8080/oauth2/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=client_credentials&client_id=<clientId>&client_secret=<clientSecret>'

# API Key 调用（Header 名可配，默认 X-API-Key）
curl -s http://localhost:8080/open/portal/apps -H 'X-API-Key: sk_xxxxxxxx'
```

支持的授权类型：`authorization_code`、`implicit`、`password`、`client_credentials`、`refresh_token`。签名方式调用见 `sign` 库（模块内 `modality.sign.keys[*]` 配置 appId 与密钥）。

### 6. 工单中心

以租户用户身份走通「创建 → 订阅 → 分派 → 解决 → 关闭 → 重开」全链路（管理端动作需要 `workorder:order:assign` / `workorder:order:close` 权限码）：

```bash
# 创建工单（编号按「前缀+日期+序号」自动生成）
curl -s -X POST http://localhost:8080/workorder/my \
  -H "satoken: $TOKEN" -H 'X-Login-Type: TENANT_USER' \
  -H 'Content-Type: application/json' \
  -d '{"title":"打印机故障","content":"卡纸严重","priority":"HIGH"}'

# 订阅该工单（后续 assign / comment / close 事件会发站内通知）
curl -s -X POST http://localhost:8080/workorder/my/$ORDER_ID/subscription -H "satoken: $TOKEN"

# 管理端：分派（受理）→ 标记解决 → 关闭 → 重开
curl -s -X POST http://localhost:8080/workorder/manage/$ORDER_ID/assign \
  -H "satoken: $ADMIN_TOKEN" -H 'Content-Type: application/json' -d '{"assigneeId":202}'
curl -s -X POST http://localhost:8080/workorder/manage/$ORDER_ID/resolve  -H "satoken: $ADMIN_TOKEN"
curl -s -X POST http://localhost:8080/workorder/manage/$ORDER_ID/close    -H "satoken: $ADMIN_TOKEN"
curl -s -X POST http://localhost:8080/workorder/manage/$ORDER_ID/reopen   -H "satoken: $ADMIN_TOKEN"

# 评论与催办
curl -s -X POST http://localhost:8080/workorder/my/$ORDER_ID/comments \
  -H "satoken: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"content":"已补充故障现象"}'
curl -s -X POST http://localhost:8080/workorder/manage/$ORDER_ID/urge \
  -H "satoken: $ADMIN_TOKEN" -H 'Content-Type: application/json' -d '{"message":"请尽快处理"}'

# 我的工单 / 我的订阅 / 我的通知
curl -s http://localhost:8080/workorder/my/page -H "satoken: $TOKEN"
curl -s http://localhost:8080/workorder/my/subscriptions -H "satoken: $TOKEN"
curl -s http://localhost:8080/workorder/my/notifications -H "satoken: $TOKEN"
```

挂起 / 恢复 / 转派 / 升级 / 批量重开 / 流转预览等管理端点见 `WorkOrderFlowController`；全部端点在 Swagger 的「工单中心」分组下。

### 7. 以库的方式引入

如果只需要其中某项能力，可以只依赖对应库；版本由 BOM 统一收口：

```gradle
dependencies {
    implementation platform('cn.slainc:modality-library-spring-boot-bom:0.0.1-SNAPSHOT')
    implementation 'cn.slainc:modality-library-spring-boot-tenant'
    implementation 'cn.slainc:modality-library-spring-boot-security'
}
```

库只提供能力与服务、不注册端点；端点由使用方自己声明。每项能力都能关闭：

```properties
modality.tenant.enabled=true
modality.tenant.resolver.header.enabled=false
modality.security.session.active-timeout=1800
```

## 配置速查

配置一律用 `application.properties`，前缀按模块划分：

| 前缀 | 归属 |
| --- | --- |
| `modality.tenant.*` | 多租户：开关、解析器、隔离策略、配额、生命周期、状态校验 |
| `modality.security.*` | 安全管理器：账号体系、过滤器白名单、会话、设备、Cookie、锁定、登录限制 |
| `modality.auth.*` | 认证中心：验证码、扫码、注销、令牌端点限流、记住我 |
| `modality.jwt.*` | 令牌签发：算法、密钥、TTL、轮换、吊销 |
| `modality.oauth2.*` / `modality.sso.*` / `modality.apikey.*` / `modality.sign.*` / `modality.thirdauth.*` | 各协议与第三方登录 |
| `modality.mybatis.*` / `modality.datasource.*` / `modality.redis.*` | 持久层与缓存 |
| `modality.storage.*` / `modality.infra.*` | 存储与基础设施中心 |
| `modality.web.*` | Web 通用：跨域、安全响应头、请求限流、异步、静态资源、接口文档 |
| `modality.monitor.*` / `modality.logger.*` / `modality.audit.*` | 监控、日志、审计 |
| `modality.workorder-engine.*` / `modality.workorder.*` | 工单引擎与工单中心：开关、编号规则、模板、SLA、通知 |
| `modality.mfa.*` / `modality.sse.*` / `modality.search.*` / `modality.recycle.*` | 二次验证、SSE 推送、搜索引擎、通用回收站 |
| `modality.webhook.*` / `modality.job.*` / `modality.change-audit.*` | Webhook 投递、定时任务内核、字段级变更审计 |

完整清单可直接查看各库 `autoconfigure` 包下的 `*Properties` 类，或启动后访问 `/actuator/configprops`（需先放开该端点）。

## 贡献指南

### 开发环境

- JDK 21 + Docker（本地 MySQL / Redis）
- 克隆后先跑通 `./gradlew clean build`，再开始改动
- 需要真实 Redis 的用例：`export MODALITY_REDIS_PASSWORD=<密码>`

### 代码规范（硬性）

1. **注释极少**：Java 只允许 `//` 单行注释，禁止块注释与 Javadoc；只在反直觉常量、绕过框架缺陷、不可调换的顺序处写一行；配置文件零注释
2. **文案全中文**：异常消息、日志、断言提示、枚举描述都用中文；算法名、MIME、协议头、字符集保留原文
3. **命名不带项目前缀**：类名直接表达能力（如 `TenantAutoConfiguration`）；与三方同名时换更具体的语义名
4. **包路径与模块一一对应**：`api-key` → `cn.slainc.apikey`，`open-api` → `cn.slainc.openapi`
5. **分层方向不可逆**：`utils`/`crypto` 最底层，`core` 不依赖 `web`，库不依赖业务模块，业务模块之间禁止互相依赖（跨模块能力一律下沉为「接口 + 模型」SPI）
6. **库与模块的包名互斥**：库内禁止出现 `service`、`web`、`dto`、`entity`、`mapper`、`model`、`impl`、`vo`、`form`、`controller`、`repository` 等模块独占包名（由 boot 的 ArchUnit 规则固化）；库内实现类放 `support`/`admin`/`loader`/`dao`/`domain`/`spi` 等技术词包，`cn.slainc.web` 库本体豁免
7. **REST 控制器只存在于业务模块的 `web` 包**：库内不得出现 `@RestController`/`@Controller`（`@RestControllerAdvice` 与 Actuator `@Endpoint` 例外，归 `advice`/协议装配包）
8. **业务模块统一目录样式**：必备 `autoconfigure`（装配）、`service`（业务服务，直接类禁止 impl 子包）、`web`（控制器）；按需 `config`/`dto`/`entity`/`mapper`/`store`（非 MP 存储）/`excel`/`listener`/`publisher`/`scheduler`/`aspect`/`filter`/`context` 及领域子包
6. **库不注册端点**：`src/main/java` 下不得出现 `@RestController` / `@RequestMapping`，端点由使用方声明；只有 `@RestControllerAdvice` 与 Actuator `@Endpoint` 例外
7. **自动配置**：类放 `autoconfigure` 包、每个 Bean 加 `@ConditionalOnMissingBean`、每项能力可用 `@ConditionalOnProperty` 关闭且 `matchIfMissing = true`；依赖 `compileOnly` 坐标时必须用 `@ConditionalOnClass(name = "...")` 独立注册
8. **依赖**：版本只在 `gradle/libs.versions.toml` 维护，模块里禁止写版本号字面量；能 `implementation` 就不要 `api`

### 提交规范

单行标题、不写正文：`type(scope): 中文一句话`

```
feat(tenant): 新增按域名解析租户并补一致性校验
fix(storage): 修复读路径绕过装饰链导致加密文件下载为密文
test(upms): 补角色成环校验用例
```

- `type`：`feat` / `fix` / `test` / `refactor` / `docs` / `chore`
- `scope`：模块短名（`tenant` / `security` / `upms` / `boot` / `build` 等）
- 提交粒度按「可独立描述的一个能力面」拆，**实现与测试分开提交**
- 提交前确保 `./gradlew clean build --no-build-cache` 通过

### 分支与 PR

1. 从 `main` 切出特性分支：`feat/tenant-domain-resolver`、`fix/storage-decorate-read`
2. 一个 PR 只解决一件事；标题同样使用 `type(scope): 中文一句话`
3. PR 描述包含：背景、改动范围、**验证方式（命令与结果）**、以及对使用者可见的行为变化
4. 涉及表结构、配置默认值、安全策略的改动，必须说明升级与回滚方式
5. 新增模块需同步三处：`settings.gradle` 的 `include`、模块自己的 `build.gradle`、`modality-boot` 的聚合依赖

#### 网页端操作示例：修复一个 bug（`fix/xxx` 分支）

**第 1 步：从 `main` 创建 fix 分支**

1. 打开仓库首页，点击分支下拉框（显示 `main` 的位置）
2. 输入框填 `fix/login-crash`，点击 **Create branch: fix/login-crash from main**

**第 2 步：在 fix 分支上修改代码**

1. 确认当前分支是 `fix/login-crash`
2. 浏览到要修改的文件，点击 **Edit**（铅笔图标）
3. 修改内容，填写提交信息，点击 **Commit changes**；可以多文件多次提交
4. 也可以在仓库首页按 `.` 键，直接在网页版 VS Code 中编辑，体验更接近本地

**第 3 步：提交 PR 到 `dev`**

1. 切换到 **Pull requests** 标签 → **New pull request**
2. 设置方向：`base: dev`，`compare: fix/login-crash`
3. 填写标题（如 `fix: 修复登录崩溃`）和描述 → **Create pull request**
4. 等待审查（如果设了审批要求），通过后点击 **Merge pull request**
5. 合并后点击 **Delete branch** 删除 fix 分支

**第 4 步：`dev` 合并到 `main`**

`dev` 分支积累了一批修改后：

1. **Pull requests** → **New pull request**
2. 设置方向：`base: main`，`compare: dev`
3. 创建 PR → 审批 → **Merge**

### 报告安全问题

请不要公开提交 Issue。通过私有渠道联系维护者，附上复现步骤、影响面与建议修复方案；修复发布后会在版本说明中致谢。

## 许可证

本项目采用 **GNU General Public License v3.0** 发布，全文见 [LICENSE](LICENSE)。

```
Copyright (C) 2026 modality

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.
```

GPLv3 的核心含义：你可以自由使用、修改、分发本项目，但**分发衍生作品时必须同样以 GPLv3 开源并提供完整源码**。若需闭源商用，请联系维护者讨论商业授权。
