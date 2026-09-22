package cn.slainc.modules.upms.security;

import cn.slainc.core.enums.LoginType;
import cn.slainc.modules.upms.entity.UserRecord;
import cn.slainc.modules.upms.mapper.UserMapper;
import cn.slainc.security.spi.AccountAuthProvider;
import cn.slainc.security.spi.PasswordAccount;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

// 登录账号数据源：t_user 表按 login_type + username 全局唯一，替掉配置文件的 ConfigAccountAuthProvider
public class UpmsAccountAuthProvider implements AccountAuthProvider {

    private final UserMapper userMapper;

    public UpmsAccountAuthProvider(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public PasswordAccount findAccount(String username, LoginType type) {
        if (username == null || type == null || type == LoginType.GUEST) {
            return null;
        }
        UserRecord user = userMapper.selectOne(new LambdaQueryWrapper<UserRecord>()
                .eq(UserRecord::getUsername, username)
                .eq(UserRecord::getLoginType, type.value())
                .eq(UserRecord::getDeleted, false)
                .last("LIMIT 1"));
        return toAccount(user);
    }

    @Override
    public PasswordAccount findAccountByUserId(Long userId, LoginType type) {
        if (userId == null || type == null) {
            return null;
        }
        UserRecord user = userMapper.selectOne(new LambdaQueryWrapper<UserRecord>()
                .eq(UserRecord::getId, userId)
                .eq(UserRecord::getLoginType, type.value())
                .eq(UserRecord::getDeleted, false)
                .last("LIMIT 1"));
        return toAccount(user);
    }

    private PasswordAccount toAccount(UserRecord user) {
        if (user == null) {
            return null;
        }
        LocalDateTime changedAt = user.getPasswordChangedAt() == null ? null
                : LocalDateTime.ofInstant(Instant.ofEpochMilli(user.getPasswordChangedAt()), ZoneId.systemDefault());
        return new PasswordAccount(user.getId(), user.getUsername(), user.getPasswordHash(), user.enabled(),
                changedAt, Boolean.TRUE.equals(user.getMustChangePassword()));
    }
}
