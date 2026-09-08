package cn.slainc.security.stp;

import cn.dev33.satoken.stp.StpInterface;
import cn.slainc.core.enums.LoginType;
import cn.slainc.security.cache.GrantedSet;
import cn.slainc.security.cache.SecurityPermissionCache;
import cn.slainc.security.LoginTypes;

import java.util.List;

public class StpInterfaceImpl implements StpInterface {

    private final SecurityPermissionCache cache;

    public StpInterfaceImpl(SecurityPermissionCache cache) {
        this.cache = cache;
    }

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return granted(loginId, loginType).permissions();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return granted(loginId, loginType).roles();
    }

    private GrantedSet granted(Object loginId, String loginType) {
        LoginType type = LoginTypes.resolve(loginType);
        return cache.load(loginId, type);
    }
}
