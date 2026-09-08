package cn.slainc.security;

import cn.slainc.core.enums.LoginType;
import cn.slainc.security.spi.SecurityPermissionProvider;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class CountingPermissionProvider implements SecurityPermissionProvider {

    public final AtomicInteger permissionCalls = new AtomicInteger();

    public final AtomicInteger roleCalls = new AtomicInteger();

    @Override
    public List<String> getPermissions(Object loginId, LoginType type) {
        permissionCalls.incrementAndGet();
        if ("2999".equals(String.valueOf(loginId)) && type == LoginType.USER) {
            return List.of("guest:ping");
        }
        if (type == LoginType.ADMIN) {
            return List.of("system:user:list", "system:user:kick");
        }
        if (type == LoginType.USER) {
            return List.of("order:read", "order:write", "user:info");
        }
        if (type == LoginType.CLIENT) {
            return List.of("client:sync");
        }
        return List.of();
    }

    @Override
    public List<String> getRoles(Object loginId, LoginType type) {
        roleCalls.incrementAndGet();
        if ("2999".equals(String.valueOf(loginId)) && type == LoginType.USER) {
            return List.of();
        }
        if (type == LoginType.ADMIN) {
            return List.of("admin");
        }
        if (type == LoginType.USER) {
            return List.of("user");
        }
        if (type == LoginType.CLIENT) {
            return List.of("client");
        }
        return List.of();
    }
}
