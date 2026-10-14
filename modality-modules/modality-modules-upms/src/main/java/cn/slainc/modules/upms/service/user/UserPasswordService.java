package cn.slainc.modules.upms.service.user;

import cn.slainc.core.enums.LoginType;
import cn.slainc.core.exception.BizException;
import cn.slainc.crypto.hash.BCryptUtils;
import cn.slainc.modules.upms.entity.UserRecord;
import cn.slainc.modules.upms.mapper.UserMapper;
import cn.slainc.modules.upms.security.UpmsPermissionCacheInvalidator;
import cn.slainc.security.password.PasswordPolicyService;
import cn.slainc.security.spi.AccountPasswordUpdater;

import java.util.List;

// 密码管理：管理员重置与用户自助修改都复用 security 的密码策略（强度/历史/过期），落库走 AccountPasswordUpdater
public class UserPasswordService {

    private final UserService userService;

    private final UserMapper userMapper;

    private final PasswordPolicyService passwordPolicy;

    private final AccountPasswordUpdater passwordUpdater;

    private final UpmsPermissionCacheInvalidator cacheInvalidator;

    public UserPasswordService(UserService userService, UserMapper userMapper,
                               PasswordPolicyService passwordPolicy, AccountPasswordUpdater passwordUpdater,
                               UpmsPermissionCacheInvalidator cacheInvalidator) {
        this.userService = userService;
        this.userMapper = userMapper;
        this.passwordPolicy = passwordPolicy;
        this.passwordUpdater = passwordUpdater;
        this.cacheInvalidator = cacheInvalidator;
    }

    // 管理员重置：新密码走强度校验，命中历史密码直接拒绝，重置后强制用户下次登录改密
    public void resetByAdmin(Long userId, String newPassword) {
        UserRecord user = userService.get(userId);
        LoginType type = LoginType.of(user.getLoginType());
        if (passwordPolicy == null) {
            throw new BizException("密码策略服务未装配，无法重置密码");
        }
        List<String> violations = passwordPolicy.validateStrength(newPassword);
        if (!violations.isEmpty()) {
            throw new BizException(String.join("；", violations));
        }
        if (passwordPolicy.isUsedInHistory(userId, type, newPassword)) {
            throw new BizException("新密码与历史密码重复，请换一个");
        }
        String newHash = BCryptUtils.hash(newPassword);
        passwordUpdater.updatePassword(userId, newHash, type);
        UserRecord fresh = userService.get(userId);
        fresh.setMustChangePassword(true);
        fresh.setUpdateTime(System.currentTimeMillis());
        userMapper.updateById(fresh);
        // 密码历史存 BCrypt 哈希（与 security 的自助改密链路口径一致），isUsedInHistory 按哈希校验比对
        passwordPolicy.recordHistory(userId, type, newHash);
        cacheInvalidator.invalidateUser(userId, type);
    }

    // 用户自助修改：校验旧密码，成功后清除强制改密标记并踢掉其它会话
    public void changeBySelf(Long userId, LoginType type, String oldPassword, String newPassword) {
        passwordPolicy.changePassword(type, userId, oldPassword, newPassword, true);
    }

    public boolean mustChangePassword(Long userId, LoginType type) {
        UserRecord user = userService.get(userId);
        return Boolean.TRUE.equals(user.getMustChangePassword()) || passwordPolicy.isExpired(userId, type);
    }
}
