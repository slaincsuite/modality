package cn.slainc.modules.upms.security;

import cn.slainc.core.enums.LoginType;
import cn.slainc.modules.upms.entity.UserRecord;
import cn.slainc.modules.upms.mapper.UserMapper;
import cn.slainc.security.spi.AccountPasswordUpdater;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

// security 改密链路（PasswordPolicyService.changePassword）的落库端：写 t_user 密码哈希并清除强制改密标记
public class UpmsAccountPasswordUpdater implements AccountPasswordUpdater {

    private final UserMapper userMapper;

    public UpmsAccountPasswordUpdater(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public void updatePassword(Long userId, String newPasswordBcrypt, LoginType type) {
        if (newPasswordBcrypt == null || !newPasswordBcrypt.startsWith("$2")
                || newPasswordBcrypt.length() != 60) {
            throw new IllegalArgumentException("写入的密码必须是 BCrypt 哈希");
        }
        UserRecord user = userMapper.selectOne(new LambdaQueryWrapper<UserRecord>()
                .eq(UserRecord::getId, userId)
                .eq(UserRecord::getLoginType, type.value())
                .eq(UserRecord::getDeleted, false)
                .last("LIMIT 1"));
        if (user == null) {
            throw new IllegalStateException("用户不存在或已注销: " + userId + "，无法更新密码");
        }
        user.setPasswordHash(newPasswordBcrypt);
        user.setMustChangePassword(false);
        user.setPasswordChangedAt(System.currentTimeMillis());
        user.setUpdateTime(System.currentTimeMillis());
        userMapper.updateById(user);
    }
}
