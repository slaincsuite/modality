package cn.slainc.modules.upms.security;

import cn.slainc.core.enums.LoginType;
import cn.slainc.modules.upms.entity.UserRecord;
import cn.slainc.modules.upms.mapper.UserMapper;
import cn.slainc.modules.upms.service.AccessControlService;
import cn.slainc.modules.upms.service.RoleService;
import cn.slainc.modules.upms.service.SystemRegistryService;
import cn.slainc.security.spi.SecurityPermissionProvider;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// RBAC 权限数据源：角色链权限码 + PUBLIC 系统 access 码 + PRIVATE 授权 access 码，供 security 的缓存合并策略来源使用
public class UpmsPermissionProvider implements SecurityPermissionProvider {

    private static final Logger log = LoggerFactory.getLogger(UpmsPermissionProvider.class);

    private final UserMapper userMapper;

    private final RoleService roleService;

    private final SystemRegistryService systemRegistryService;

    private final AccessControlService accessControlService;

    public UpmsPermissionProvider(UserMapper userMapper, RoleService roleService,
                                  SystemRegistryService systemRegistryService,
                                  AccessControlService accessControlService) {
        this.userMapper = userMapper;
        this.roleService = roleService;
        this.systemRegistryService = systemRegistryService;
        this.accessControlService = accessControlService;
    }

    @Override
    public List<String> getPermissions(Object loginId, LoginType type) {
        Long userId = parseLoginId(loginId);
        if (userId == null || !manages(type)) {
            return List.of();
        }
        UserRecord user = userMapper.selectById(userId);
        if (user == null || Boolean.TRUE.equals(user.getDeleted()) || !user.enabled()) {
            return List.of();
        }
        Set<String> permissions = new LinkedHashSet<>(roleService.resolvePermissionCodes(userId));
        for (var system : systemRegistryService.enabledPublicSystems()) {
            permissions.add(system.accessPermissionCode());
        }
        permissions.addAll(accessControlService.grantedAccessCodes(userId));
        return new ArrayList<>(permissions);
    }

    @Override
    public List<String> getRoles(Object loginId, LoginType type) {
        Long userId = parseLoginId(loginId);
        if (userId == null || !manages(type)) {
            return List.of();
        }
        UserRecord user = userMapper.selectById(userId);
        if (user == null || Boolean.TRUE.equals(user.getDeleted()) || !user.enabled()) {
            return List.of();
        }
        return roleService.resolveRoleCodes(userId);
    }

    public boolean userActive(Long userId, LoginType type) {
        UserRecord user = userMapper.selectOne(new LambdaQueryWrapper<UserRecord>()
                .eq(UserRecord::getId, userId).eq(UserRecord::getLoginType, type.value())
                .eq(UserRecord::getDeleted, false).last("LIMIT 1"));
        return user != null && user.enabled();
    }

    // UPMS 只管理四类「人」的账号体系；APP/SERVICE/GUEST 的权限来自开放平台与 API Key 体系
    private boolean manages(LoginType type) {
        return type == LoginType.PLATFORM_ADMIN || type == LoginType.PLATFORM_USER
                || type == LoginType.TENANT_ADMIN || type == LoginType.TENANT_USER;
    }

    private Long parseLoginId(Object loginId) {
        if (loginId == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(loginId));
        } catch (NumberFormatException e) {
            log.debug("非数值 loginId 不属于 UPMS 管理范围: {}", loginId);
            return null;
        }
    }
}
