package cn.slainc.modules.upms.security;

import cn.slainc.core.enums.LoginType;
import cn.slainc.modules.upms.entity.UserRecord;
import cn.slainc.modules.upms.service.user.UserService;
import cn.slainc.security.spi.AccountRestrictionPort;

import java.util.List;

// 用户级登录限制（IP/时段/设备）的裁决端：按用户名定位账号后交由 UserService 比对限制规则
public class UpmsAccountRestrictionPort implements AccountRestrictionPort {

    private final UserService userService;

    public UpmsAccountRestrictionPort(UserService userService) {
        this.userService = userService;
    }

    @Override
    public List<String> validate(String username, LoginType type, String clientIp, String deviceType) {
        if (username == null || type == null) {
            return List.of();
        }
        UserRecord user = userService.findByUsername(username, type);
        return user == null ? List.of() : userService.validateRestrictions(user, clientIp, deviceType);
    }
}
