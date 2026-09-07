package cn.slainc.security.spi;

import cn.slainc.core.enums.LoginType;

public interface AccountPasswordUpdater {

    void updatePassword(Long userId, String newPasswordBcrypt, LoginType type);
}
