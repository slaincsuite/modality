package cn.slainc.security.spi;

import cn.slainc.core.enums.LoginType;

public interface AccountAuthProvider {

    /** Resolve by exact username first, then by the account's configured phone number. */
    PasswordAccount findAccount(String loginIdentity, LoginType type);

    default PasswordAccount findAccountByUserId(Long userId, LoginType type) {
        return null;
    }
}
