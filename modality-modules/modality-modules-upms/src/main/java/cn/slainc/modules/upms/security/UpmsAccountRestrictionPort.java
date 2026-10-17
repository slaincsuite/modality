package cn.slainc.modules.upms.security;

import cn.slainc.core.enums.LoginType;
import cn.slainc.modules.upms.entity.UserRecord;
import cn.slainc.modules.upms.mapper.UserMapper;
import cn.slainc.modules.upms.service.user.UserService;
import cn.slainc.security.spi.AccountRestrictionPort;

import java.util.List;

// 用户级登录限制（IP/时段/设备）的裁决端：账号或手机号必须命中同一个登录账号
public class UpmsAccountRestrictionPort implements AccountRestrictionPort {

    private final UserService userService;

    private final UserMapper userMapper;

    public UpmsAccountRestrictionPort(UserService userService, UserMapper userMapper) {
        this.userService = userService;
        this.userMapper = userMapper;
    }

    @Override
    public List<String> validate(String loginIdentity, LoginType type, String clientIp, String deviceType) {
        if (loginIdentity == null || type == null) {
            return List.of();
        }
        UserRecord user = UpmsLoginIdentityLookup.resolve(userMapper, loginIdentity, type);
        return user == null ? List.of() : userService.validateRestrictions(user, clientIp, deviceType);
    }
}
