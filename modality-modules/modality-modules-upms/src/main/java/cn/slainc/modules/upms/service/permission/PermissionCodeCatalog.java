package cn.slainc.modules.upms.service.permission;

import cn.slainc.modules.upms.mapper.PermissionMapper;
import cn.slainc.security.util.PermissionWildcardMatcher;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// 权限码目录读多写少：通配符边界校验不应每次全表扫描，写操作主动失效并允许短 TTL 兜底
public class PermissionCodeCatalog {

    private final PermissionMapper permissionMapper;

    private final long ttlMillis;

    private volatile Snapshot snapshot;

    public PermissionCodeCatalog(PermissionMapper permissionMapper, long ttlSeconds) {
        this.permissionMapper = permissionMapper;
        this.ttlMillis = Math.max(1, ttlSeconds) * 1000;
    }

    public boolean contains(String code) {
        return current().codes().contains(code);
    }

    public boolean matches(String wildcard) {
        for (String code : current().codes()) {
            if (PermissionWildcardMatcher.matches(wildcard, code)) {
                return true;
            }
        }
        return false;
    }

    public List<String> codes() {
        return List.copyOf(current().codes());
    }

    public void invalidate() {
        snapshot = null;
    }

    private Snapshot current() {
        long now = System.currentTimeMillis();
        Snapshot current = snapshot;
        if (current != null && now - current.loadedAt() < ttlMillis) {
            return current;
        }
        synchronized (this) {
            current = snapshot;
            if (current != null && System.currentTimeMillis() - current.loadedAt() < ttlMillis) {
                return current;
            }
            Set<String> codes = new LinkedHashSet<>();
            permissionMapper.selectList(null).stream()
                    .map(permission -> permission.getCode())
                    .filter(code -> code != null && !code.isBlank())
                    .forEach(codes::add);
            Snapshot fresh = new Snapshot(Set.copyOf(codes), System.currentTimeMillis());
            snapshot = fresh;
            return fresh;
        }
    }

    private record Snapshot(Set<String> codes, long loadedAt) {
    }
}
