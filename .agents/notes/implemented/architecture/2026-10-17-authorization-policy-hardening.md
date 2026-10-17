# Agent Note: 权限委派与策略治理加固

Status: implemented

## Problem

RBAC 与策略权限合并后，用户权限边界只作用于 RBAC 来源，策略仍可绕过边界；租户内角色/用户组策略只按角色码或组编码命中，可能跨租户串用；拥有授权权限的账号还能把自身未持有的权限或高权角色转授出去。策略生成与直接 JSON 提交也缺少统一容量上限。

## Decision

- 权限匹配统一支持 `*` 通配符；策略拒绝以 `!pattern` 进入合并权限集，匹配时拒绝优先。用户权限边界在 RBAC 与策略合并完成后再次过滤。
- 角色/用户组策略绑定使用 `tenant-role` / `tenant-group`，主体标识为 `租户ID:角色或用户组编码`；原有 `role` / `group` 保留为显式跨租户主体。
- 授权委派不得超出操作者当前有效权限：分配权限、分配角色、角色关联用户组、用户组关联成员/角色均经过同一委派校验。
- 策略变更事件清空权限缓存；权限定义目录使用短 TTL 缓存，写操作主动失效，避免通配符边界校验全表扫描。
- `@RequirePolicy` 自动解析当前账号体系，并向条件环境注入登录体系、角色、用户组、来源 IP、请求方法/路径、User-Agent 与二次认证安全窗口。
- 策略生成器支持 20 个预设、完整动作目录、组合条件与危险动作 DENY 护栏；直传文档限制 1 MiB、128 条语句、单语句 256 个 action/resource。

## Alternatives considered

**只在 RBAC 侧保留权限边界**：策略权限仍可越过边界，放弃。

**用角色码直接表达租户内角色策略**：角色码仅在租户内唯一，无法阻止跨租户命中，放弃。

**允许管理员转授任意权限**：会让“可分配权限”等同于“拥有全部权限”，放弃。

## Consequences

- 新增 `SecurityPermissionFilter` SPI，安全缓存可在合并后执行项目级权限过滤。
- 旧 `role` / `group` 策略语义保持兼容，但新建租户内绑定应使用新主体类型。
- SSO IdP CRUD 与租户会话切换仍不在本次范围，后续需单独定义存储、加密、审计与跨租户会话一致性模型。

## Related

- `.agents/notes/implemented/architecture/2026-10-17-ui-backend-gap-closure.md`
- `.agents/notes/implemented/architecture/2026-10-16-authz-terminology-iam-rbac-cam.md`
