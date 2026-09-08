package cn.slainc.security;

import cn.slainc.core.enums.LoginType;
import cn.slainc.security.cache.GrantedSet;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionMergeCacheAcceptanceTest extends SecurityAcceptanceTestBase {

    @Autowired
    CountingPermissionProvider countingProvider;

    @Test
    void mergesRbacAndPolicyAndDeduplicates() {
        var response = get("/test/permissions?type=USER&loginId=2001", null, null);
        var permissions = listOf(response, "");
        assertThat(permissions).containsExactlyInAnyOrder("order:read", "order:write", "user:info", "order:export");
        assertThat(permissions).doesNotHaveDuplicates();
    }

    @Test
    void mergePreservesRbacOrderFirst() {
        var permissions = listOf(get("/test/permissions?type=USER&loginId=2001", null, null), "");
        assertThat(permissions.indexOf("order:read")).isLessThan(permissions.indexOf("order:export"));
    }

    @Test
    void rolesMergedFromBothProviders() {
        var roles = listOf(get("/test/roles?type=USER&loginId=2001", null, null), "");
        assertThat(roles).containsExactlyInAnyOrder("user", "operator");
    }

    @Test
    void cacheHitAvoidsSecondProviderCall() {
        long before = countingProvider.permissionCalls.get();
        listOf(get("/test/permissions?type=USER&loginId=2001", null, null), "");
        long afterFirst = countingProvider.permissionCalls.get();
        assertThat(afterFirst).isEqualTo(before + 1);
        listOf(get("/test/permissions?type=USER&loginId=2001", null, null), "");
        assertThat(countingProvider.permissionCalls.get()).isEqualTo(afterFirst);
    }

    @Test
    void cachedValuePersistsInRedisWithTtl() {
        listOf(get("/test/permissions?type=USER&loginId=2001", null, null), "");
        String key = "modality:security:perm:USER:2001";
        assertThat(redis.opsForValue().get(key)).contains("order:read");
        Long ttl = redis.getExpire(key);
        assertThat(ttl).isBetween(240L, 300L);
    }

    @Test
    void invalidateByTypeForcesProviderRecall() {
        listOf(get("/test/permissions?type=USER&loginId=2001", null, null), "");
        long before = countingProvider.permissionCalls.get();
        post("/test/cache/invalidate?loginId=2001&type=USER", null, null, null);
        assertThat(redis.opsForValue().get("modality:security:perm:USER:2001")).isNull();
        listOf(get("/test/permissions?type=USER&loginId=2001", null, null), "");
        assertThat(countingProvider.permissionCalls.get()).isEqualTo(before + 1);
    }

    @Test
    void invalidateAllTypesClearsEverySystem() {
        listOf(get("/test/permissions?type=USER&loginId=2001", null, null), "");
        listOf(get("/test/permissions?type=ADMIN&loginId=1001", null, null), "");
        post("/test/cache/invalidate?loginId=2001", null, null, null);
        assertThat(redis.opsForValue().get("modality:security:perm:USER:2001")).isNull();
        assertThat(redis.opsForValue().get("modality:security:perm:ADMIN:2001")).isNull();
    }

    @Test
    void hotTtlChangeTakesEffectOnNextWrite() {
        config.set("modality.security.permission-cache-ttl-seconds", "120");
        listOf(get("/test/permissions?type=CLIENT&loginId=3001", null, null), "");
        Long ttl = redis.getExpire("modality:security:perm:CLIENT:3001");
        assertThat(ttl).isBetween(60L, 120L);
    }

    @Test
    void providerIsolatedPerType() {
        GrantedSet admin = loadGranted("ADMIN", 1001L);
        assertThat(admin.permissions()).containsExactly("system:user:list", "system:user:kick");
        assertThat(admin.roles()).containsExactly("admin");
    }

    @Test
    void clientSystemHasOwnPermissions() {
        var permissions = listOf(get("/test/permissions?type=CLIENT&loginId=3001", null, null), "");
        assertThat(permissions).containsExactly("client:sync");
    }

    private GrantedSet loadGranted(String type, Long loginId) {
        var permissions = listOf(get("/test/permissions?type=" + type + "&loginId=" + loginId, null, null), "");
        var roles = listOf(get("/test/roles?type=" + type + "&loginId=" + loginId, null, null), "");
        return new GrantedSet(permissions, roles);
    }
}
