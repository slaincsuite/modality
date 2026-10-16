# Agent Note: 授权体系术语口径（IAM / RBAC / CAM）

Status: implemented

## Problem

平台的授权能力分散在三处：`upms` 的角色 / 权限 / 数据范围、`security` 库的注解与 SPI、`policy` 库的策略文档引擎。缺少统一说法时，评审与对外文档会在「这算 RBAC 还是 ABAC」「策略要不要接腾讯云 CAM / AWS IAM」之间反复纠缠：README 此前只写「基于属性的访问控制」，读不出与既有云厂商策略语义的对应关系，也解释不了 deny 优先、无 allow 默认拒这些约定的出处。

## Decision

2026-10-16 起，仓库统一按三层口径描述授权体系（README「认证与安全」与 `AGENTS.md` 硬性约定已同步）：

- **大范畴 IAM**：认证（七套账号体系、令牌、会话）+ 授权（RBAC + 策略）+ 审计与协议接入
- **授权骨架 RBAC**：用户 / 角色 / 权限码（`upms:*` 三段式）/ 用户组 / 菜单 / 数据范围 / 访问授权，落在 `t_user_role`、`t_role_permission`、`t_role_data_scope` 等表，以及 `@RequirePermission`、`@RequireLogin` 注解
- **策略子系统 CAM 式**（= AWS IAM Policy）：`PolicyDocument`(version 2.0) + `PolicyStatement`(sid / effect / actions / resources / condition) + 主体 `global / tenant / role / user` + 12 个条件运算符；判定为 ABAC——`PolicyEngine` deny 优先、无 allow 默认拒；能力入口是 `@RequirePolicy` 与 `SecurityPolicyProvider` SPI（`PolicySecurityBridge`）

术语对应关系：IAM ⊃ RBAC；CAM 是云厂商对 IAM 的产品化命名，其「策略」即 Policy Document。文档、注释与提交信息统一用「RBAC 骨架 + 策略文档型 ABAC」表述，禁止写成「接入云 CAM / IAM」。

## Alternatives considered

- **只写「基于属性的访问控制（ABAC）」**：准确但不指向具体语义，读者无法把它与 AWS IAM Policy / 腾讯云 CAM 策略对上，也解释不了 deny 优先、无 allow 默认拒的出处。放弃。
- **直接自称 CAM 或 IAM**：容易被读成引入了云厂商 SDK 与云账号体系，而本实现是自研字符串 action/resource + 通配符，没有 AK/SK、ARN 与云资源概念。放弃。
- **把口径写进包名或类名（如 `CamPolicyEngine`）**：大面积重命名换来的只是命名对齐，术语落在文档与注释已足够。放弃。

## Consequences

- 收益：评审、文档、注释与提交信息口径一致；新同学能一眼把平台授权能力映射到已有的 IAM / RBAC / CAM 知识
- 代价：术语变长（「策略文档型 ABAC」），且新增授权能力时要先判断它属于哪一层，再按三层口径描述
- 边界：本口径只声明语义对齐，不代表任何云厂商依赖；`action` / `resource` 始终是平台自定义字符串 + `*` 通配符

## Related

- 判定链路与实现位置：README「认证与安全」「审计与策略」两节，以及 `modality-library-spring-boot-policy` 的 `engine/PolicyEngine`、`bridge/PolicySecurityBridge`
