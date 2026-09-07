package cn.slainc.security.spi;

import cn.slainc.core.enums.LoginType;

public interface AccountAuthProvider {

    PasswordAccount findAccount(String username, LoginType type);

    default PasswordAccount findAccountByUserId(Long userId, LoginType type) {
        return null;
    }
}
