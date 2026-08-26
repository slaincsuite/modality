# modality-library-spring-boot-tenant

多租户能力库：租户上下文生命周期、租户识别、隔离策略、配额、租户管理与生命周期。

包路径 `cn.slainc.tenant`，默认关闭，需显式开启：

```yaml
modality:
  tenant:
    enabled: true
```

## 1. 能力开关

| 配置项 | 默认 | 说明 |
| --- | --- | --- |
| `modality.tenant.enabled` | `false` | 总开关，关闭时本库不注册任何 Bean |
| `modality.tenant.filter.enabled` | `true` | 注册 `TenantFilter`（order 为 `FilterOrder.TENANT = -6500`） |
| `modality.tenant.resolver.header.enabled` | `true` | 请求头解析（默认 `X-Tenant-Id`） |
| `modality.tenant.resolver.domain.enabled` | `true` | 子域名解析，配合 `root-domain` 使用 |
| `modality.tenant.resolver.path.enabled` | `true` | 路径前缀解析，默认前缀 `/api` |
| `modality.tenant.resolver.param.enabled` | `true` | 请求参数解析，默认参数名 `tenantId` |
| `modality.tenant.resolver.session.enabled` | `true` | Sa-Token 会话解析（需运行期存在 sa-token-core，配置类独立注册） |
| `modality.tenant.isolation.enabled` | `true` | 隔离策略分发开关 |
| `modality.tenant.isolation.schema.enabled` | `true` | Schema 隔离处理器 |
| `modality.tenant.isolation.database.enabled` | `true` | 库级隔离处理器 |
| `modality.tenant.quota.enabled` | `true` | 配额服务 |
| `modality.tenant.lifecycle.enabled` | `true` | 租户生命周期与默认数据初始化 |
| `modality.tenant.service.enabled` | `true` | 租户 CRUD（需要运行期存在 `SqlSessionFactory`） |

解析器按 `order` 依次尝试，第一个返回非空的生效：请求头 100 → 子域名 200 → 路径 300 → 参数 400 → 会话 500。

## 2. 租户上下文

`TenantFilter` 解析出租户后写入 `cn.slainc.core.context.TenantContext`，并在 `finally` 中 `clear()`，正常与异常路径都会清理。跨线程传递使用 core 模块的 `ContextTaskDecorator` / `ContextSnapshot`。

```java
TenantHelper.callWith(2L, () -> service.list());   // 临时切换并恢复
TenantHelper.ignore(() -> mapper.selectList(null)); // 临时跳过租户条件
TenantCrossAccess.queryAs(2L, () -> mapper.count()); // 指定租户查询
TenantCrossAccess.forEachTenant(List.of(1L, 2L), id -> mapper.count()); // 跨租户统计，仅超级管理员
```

`cn.slainc.tenant.helper.TenantHelper` 是本库的便捷门面，底层复用 `cn.slainc.mybatis.tenant.TenantHelper`（忽略深度）与 `TenantContext`（切换/恢复），两者不冲突。

## 3. 表白名单：两套配置的关系

- `modality.mybatis.tenant.ignore-tables` / `ignore-table-prefixes`
  MyBatis 租户拦截器真正读取的白名单，租户过滤默认开启（fail-closed），未列入的表必须带 `tenant_id` 列。
- `modality.tenant.ignore-tables` / `ignore-table-prefixes`
  租户库自己声明的白名单。启动时 `TenantTableWhitelist` 会把它**合并**进上面那套 MyBatis 配置（去重，不覆盖原有项），默认包含 `t_tenant`、`t_tenant_quota`、`t_tenant_package`。

结论：两套配置最终在 MyBatis 侧生效，取并集；只在 `modality.tenant.*` 里配置也能生效，但反过来写在 `modality.mybatis.*` 里的项不会被租户库感知（仅 MyBatis 生效）。方法级临时跳过用 `@IgnoreTenant`（Mapper 方法/接口），运行期临时跳过用 `TenantHelper.ignore(...)`。

## 4. 隔离策略

| 策略 | 处理器 | 行为 |
| --- | --- | --- |
| `ROW` | `RowTenantIsolationHandler` | 无需额外操作，由 MyBatis 租户拦截器按 `tenant_id` 过滤 |
| `SCHEMA` | `SchemaTenantIsolationHandler` | PostgreSQL/H2/人大金仓执行 `CREATE SCHEMA IF NOT EXISTS`，MySQL/TiDB/OceanBase 执行 `CREATE DATABASE IF NOT EXISTS`，并把 `search_path`（PG 系）或 `currentSchema`（MySQL 系）写入租户元数据；Oracle/SQL Server/达梦/神通明确拒绝 |
| `DATABASE` | `DatabaseTenantIsolationHandler` | 调用 `DynamicDataSourceManager.register` 注册名为 `tenant_<租户ID>` 的数据源，用 `DynamicDataSourceContextHolder` 切换 |

策略来源优先级：租户记录上的 `isolationStrategy` → 动态配置 `tenant.isolation.strategy.<租户ID>` → `tenant.isolation.strategy` → 本地 `modality.tenant.isolation.strategy`。

库级隔离需要连接模板：

```yaml
modality:
  tenant:
    isolation:
      database:
        group-name: tenant
        url-template: jdbc:mysql://127.0.0.1:3306/tenant_{tenantCode}
        username-template: tenant_{tenantId}
        password-template: change-me
        driver-class-name: com.mysql.cj.jdbc.Driver
```

## 5. 配额

```yaml
modality:
  tenant:
    quota:
      warning-ratio: 0.8
      defaults:
        max-users: 100
        max-storage: 0
        max-api-qps: 0
        max-api-daily: 0
```

单租户热覆盖键为 `tenant.quota.<租户ID>.maxUsers` / `maxStorage` / `maxApiQps` / `maxApiDaily`。限额为 `0` 表示不限制；使用率 ≥ `warning-ratio` 时 `check` 返回告警并写中文告警日志，超限时 `require` 抛 `BizException`。

## 6. 租户表 DDL（MySQL 8）

```sql
CREATE TABLE t_tenant (
    id                 BIGINT       NOT NULL COMMENT '主键',
    tenant_code        VARCHAR(64)  NOT NULL COMMENT '租户编码',
    tenant_name        VARCHAR(128) NOT NULL COMMENT '租户名称',
    contact_name       VARCHAR(64)           COMMENT '联系人',
    contact_phone      VARCHAR(32)           COMMENT '联系电话',
    contact_email      VARCHAR(128)          COMMENT '联系邮箱',
    expire_at          DATETIME              COMMENT '过期时间',
    status             VARCHAR(16)  NOT NULL DEFAULT 'TRIAL' COMMENT 'TRIAL/ACTIVE/EXPIRED/FROZEN/DISABLED',
    isolation_strategy VARCHAR(16)  NOT NULL DEFAULT 'ROW' COMMENT 'ROW/SCHEMA/DATABASE',
    schema_name        VARCHAR(64)           COMMENT '隔离 schema',
    datasource_name    VARCHAR(64)           COMMENT '隔离数据源名',
    package_id         BIGINT                COMMENT '套餐 ID',
    remark             VARCHAR(255)          COMMENT '备注',
    created_at         DATETIME              COMMENT '创建时间',
    updated_at         DATETIME              COMMENT '更新时间',
    created_by         VARCHAR(64)           COMMENT '创建人',
    updated_by         VARCHAR(64)           COMMENT '更新人',
    deleted            TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    version            INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    tenant_id          BIGINT                COMMENT '租户 ID（平台侧为空）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_code (tenant_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '租户';
```

`t_tenant` 属于白名单表，`TenantMapper` 上标注了 `@IgnoreTenant`，租户表本身不会被租户条件过滤。

## 7. 缓存与动态配置

- 本地缓存使用 Caffeine，key 前缀为 `CacheKeys.TENANT`（`modality:tenant:`），容量与 TTL 由 `modality.tenant.cache.maximum-size` / `expire-after-write` 控制；不强制依赖 Redis。
- 运行期存在 `CacheInvalidationPublisher`（redis 模块）时，缓存失效会广播到其它节点；不存在时退化为纯本地缓存。
- 动态配置（`DynamicConfigService`）用于隔离策略、配额规则与租户默认数据模板，全部通过 `ObjectProvider` 可选注入，缺失时回退本地配置。

## 8. 测试

```bash
./gradlew :modality-library:modality-library-spring-boot-tenant:test --rerun-tasks
```

`TenantIsolationIntegrationTest` 使用真实 H2 内存库 + MyBatis-Plus 租户拦截器验证跨租户隔离，并断言真实执行到数据库的 SQL 带上了 `tenant_id` 条件。模块 `build.gradle` 目前未声明 H2，`H2TestDatabase` 会优先使用类路径驱动，类路径缺失时从 Gradle 本地缓存加载 `com.h2database:h2`；若后续把 `testRuntimeOnly libs.h2` 加入本模块（与 datasource 模块一致），该回退逻辑自动失效。若两者都不可用，测试会以明确的中文提示失败，不会静默跳过。
