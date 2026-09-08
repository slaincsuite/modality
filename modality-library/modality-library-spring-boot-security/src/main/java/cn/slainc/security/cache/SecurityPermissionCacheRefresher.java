package cn.slainc.security.cache;

import cn.slainc.core.enums.LoginType;

public interface SecurityPermissionCacheRefresher {

    void invalidate(Object loginId);

    void invalidate(Object loginId, LoginType type);
}
