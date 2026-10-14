package cn.slainc.mfa.spi;

import cn.slainc.core.context.UserContext;

public interface MfaUserLoader {

    UserContext.UserInfo loadByUserId(Long userId);
}
