---
name: modality-spi
description: 在 modality 做跨模块能力下沉或接入库的 SPI 扩展点时使用，覆盖「接口 + 模型」定义位置、实现注册、条件覆盖与装配顺序。
---

# SPI（跨模块能力）

场景：一项能力被多个层需要，但**业务模块之间禁止互相依赖**、库不能依赖模块——能力必须以「接口 + 模型」下沉到库，模块提供实现。绕过它硬 import 会被 ArchUnit 拒绝。

## 三层角色

- **服务定义**（库）：接口 + 参数 / 返回模型（record），放库内 `spi` 包
- **服务提供者**（模块）：实现类放模块内（如 `upms/security/`），由模块 `autoconfigure` 注册
- **消费者**：注入接口使用；库侧用 `ObjectProvider` 容忍缺省

## 步骤

1. **定义接口**：库 `spi` 包内，只含能力语义与模型，不含存储 / 框架细节
2. **实现**：具体模块实现（如工单引擎的存储 SPI 由模块用 MyBatis 提供）
3. **注册**：模块 `autoconfigure` 里 `@Bean @ConditionalOnMissingBean(接口.class)`，宿主可整体覆盖；需要抢在库的默认实现之前生效时用 `@AutoConfiguration(beforeName = "库 AutoConfiguration 全名")`（参考 `UpmsAutoConfiguration` 抢在 security / mybatis / policy 之前）
4. **消费**：库内注入口用 `ObjectProvider<T>`，缺省不装配而不是启动失败
5. **测试**：模块测试验证实现行为；boot 验收验证装配后的完整链路

## 既有范例

| 能力 | 定义（库） | 实现（模块） |
| --- | --- | --- |
| 账号认证 | `cn.slainc.security.spi.AccountAuthProvider` | `upms/security/UpmsAccountAuthProvider` |
| 权限查询 | `security.spi.SecurityPermissionProvider` | `UpmsPermissionProvider` |
| 策略绑定存储 | `cn.slainc.policy.storage.PolicyBindingStore` | `upms/service/policy/UpmsPolicyBindingStore` |
| 工单存储 | `cn.slainc.workorder.spi.WorkOrderStore` 等 | `workorder` 模块 MyBatis 实现 |
| 工单通知 | `workorder.spi.WorkOrderNotificationStore` | `workorder` 模块 |

## 判定口径

- 只有「一个模块要实现、其他模块 / 库要用」才建 SPI；单模块内部直接用 service
- 实现类不许放进库（库无落库依赖）；消费方不许 instanceof 判断具体实现
