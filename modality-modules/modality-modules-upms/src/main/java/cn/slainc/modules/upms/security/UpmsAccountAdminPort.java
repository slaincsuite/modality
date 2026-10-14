package cn.slainc.modules.upms.security;

import cn.slainc.modules.upms.service.user.UserService;
import cn.slainc.security.spi.AccountAdminPort;

// 账号注销链路的落库端：租户归属查询与软删复用 UserService，写 t_user 并清理角色绑定与权限缓存
public class UpmsAccountAdminPort implements AccountAdminPort {

    private final UserService userService;

    public UpmsAccountAdminPort(UserService userService) {
        this.userService = userService;
    }

    @Override
    public Long tenantIdOf(Long userId) {
        return userService.get(userId).getTenantId();
    }

    @Override
    public void delete(Long userId) {
        userService.delete(userId);
    }
}
