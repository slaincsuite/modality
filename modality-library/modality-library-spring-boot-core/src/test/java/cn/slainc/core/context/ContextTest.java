package cn.slainc.core.context;

import cn.slainc.core.enums.LoginType;
import cn.slainc.core.exception.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextTest {

    @AfterEach
    void tearDown() {
        ContextSnapshot.clearAll();
    }

    @Test
    void userContextLifecycle() {
        assertFalse(UserContext.isAuthenticated());
        assertNull(UserContext.userId());
        assertTrue(UserContext.roles().isEmpty());
        assertFalse(UserContext.hasRole("admin"));

        UserContext.UserInfo info = new UserContext.UserInfo(100L, "admin", LoginType.PLATFORM_ADMIN,
                Set.of("admin", "ops"), Set.of("user:list", "role:*"), true);
        UserContext.set(info);

        assertTrue(UserContext.isAuthenticated());
        assertEquals(100L, (long) UserContext.userId());
        assertEquals("admin", UserContext.username());
        assertEquals(LoginType.PLATFORM_ADMIN, UserContext.loginType());
        assertTrue(UserContext.hasRole("admin"));
        assertFalse(UserContext.hasRole("guest"));
        assertFalse(UserContext.hasRole(null));
        assertTrue(UserContext.hasPermission("user:list"));
        assertTrue(UserContext.hasPermission("role:assign"));
        assertFalse(UserContext.hasPermission("user:delete"));
        assertTrue(UserContext.isSuperAdmin());

        UserContext.clear();
        assertFalse(UserContext.isAuthenticated());
        assertFalse(UserContext.isSuperAdmin());
    }

    @Test
    void userInfoToleratesNullCollections() {
        UserContext.UserInfo info = new UserContext.UserInfo(1L, "u", LoginType.TENANT_USER, null, null, false);
        assertTrue(info.roles().isEmpty());
        assertTrue(info.permissions().isEmpty());
        assertEquals(1L, (long) UserContext.UserInfo.of(1L, "u", LoginType.TENANT_USER).userId());
    }

    @Test
    void tenantContextLifecycle() {
        assertNull(TenantContext.get());
        assertEquals(TenantContext.DEFAULT_TENANT_ID, TenantContext.getOrDefault());
        assertThrows(BizException.class, TenantContext::require);

        TenantContext.set(9L);
        assertEquals(9L, (long) TenantContext.require());

        assertEquals(7L, (long) TenantContext.callWith(7L, TenantContext::get));
        assertEquals(9L, (long) TenantContext.get());

        TenantContext.runWith(8L, () -> assertEquals(8L, (long) TenantContext.get()));
        TenantContext.clear();
        assertNull(TenantContext.get());
    }

    @Test
    void requestContextLifecycle() {
        assertNull(RequestContext.traceId());
        RequestContext.set(new RequestContext.RequestInfo("trace-1", "1.1.1.1", "curl", "GET", "/api"));
        assertEquals("trace-1", RequestContext.traceId());
        assertEquals("1.1.1.1", RequestContext.clientIp());
        assertEquals("curl", RequestContext.userAgent());
        assertEquals("trace-1", RequestContext.RequestInfo.of("trace-1").traceId());
        RequestContext.clear();
        assertNull(RequestContext.traceId());
        assertNull(RequestContext.clientIp());
    }

    @Test
    void snapshotCapturesAndRestores() {
        UserContext.set(UserContext.UserInfo.of(1L, "u", LoginType.TENANT_USER));
        TenantContext.set(5L);
        RequestContext.set(RequestContext.RequestInfo.of("t-1"));

        ContextSnapshot snapshot = ContextSnapshot.capture();
        assertEquals(1L, snapshot.toMap().get("userId"));
        assertEquals(5L, snapshot.toMap().get("tenantId"));
        assertEquals("t-1", snapshot.toMap().get("traceId"));

        ContextSnapshot.clearAll();
        assertNull(UserContext.userId());
        assertNull(TenantContext.get());

        ContextSnapshot.restoreAll(snapshot);
        assertEquals(1L, (long) UserContext.userId());
        assertEquals(5L, (long) TenantContext.get());
        assertEquals("t-1", RequestContext.traceId());

        ContextSnapshot.restoreAll(null);
        assertNull(UserContext.userId());
    }

    @Test
    void taskDecoratorPropagatesContext() {
        UserContext.set(UserContext.UserInfo.of(2L, "worker-user", LoginType.TENANT_USER));
        TenantContext.set(6L);

        Runnable decorated = new ContextTaskDecorator().decorate(() -> {
            assertEquals(2L, (long) UserContext.userId());
            assertEquals(6L, (long) TenantContext.get());
        });

        ContextSnapshot.clearAll();
        decorated.run();
        assertNull(UserContext.userId());
    }
}
