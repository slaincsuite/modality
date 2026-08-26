package cn.slainc.tenant.resolver;

import cn.dev33.satoken.context.mock.SaTokenContextMockUtil;
import cn.dev33.satoken.stp.StpUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionTenantResolverTest {

    private static final long TENANT_ID = 88L;

    private final MockHttpServletRequest request = new MockHttpServletRequest();

    @BeforeEach
    void setUp() {
        SaTokenContextMockUtil.setMockContext();
    }

    @AfterEach
    void tearDown() {
        if (StpUtil.isLogin()) {
            StpUtil.logout();
        }
        SaTokenContextMockUtil.clearContext();
    }

    @Test
    @DisplayName("已登录且在会话中写入 tenantId 时能解析出租户")
    void resolvesTenantFromRealSession() {
        StpUtil.login(1001L);
        StpUtil.getSession().set("tenantId", TENANT_ID);

        SessionTenantResolver resolver = new SessionTenantResolver(null);
        assertEquals(TENANT_ID, resolver.resolve(request));
        assertTrue(StpUtil.isLogin());
        assertEquals(SessionTenantResolver.ORDER, resolver.order());
    }

    @Test
    @DisplayName("会话属性为字符串数字时同样解析成功")
    void resolvesNumericTextFromSession() {
        StpUtil.login(1002L);
        StpUtil.getSession().set("tenantId", String.valueOf(TENANT_ID));

        assertEquals(TENANT_ID, new SessionTenantResolver("tenantId", null).resolve(request));
    }

    @Test
    @DisplayName("会话中存放租户编码时经 TenantLookup 解析为 ID")
    void resolvesTenantCodeThroughLookup() {
        StpUtil.login(1003L);
        StpUtil.getSession().set("tenantId", "acme");

        SessionTenantResolver resolver = new SessionTenantResolver(null, code -> "acme".equals(code) ? TENANT_ID : null);
        assertEquals(TENANT_ID, resolver.resolve(request));
    }

    @Test
    @DisplayName("未登录时返回 null 而不是抛异常")
    void returnsNullWhenNotLoggedIn() {
        assertNull(new SessionTenantResolver(null).resolve(request));
    }

    @Test
    @DisplayName("会话里没有租户属性时返回 null")
    void returnsNullWhenAttributeMissing() {
        StpUtil.login(1004L);
        assertNull(new SessionTenantResolver(null).resolve(request));
    }

    @Test
    @DisplayName("会话属性为空白时返回 null")
    void returnsNullWhenAttributeBlank() {
        StpUtil.login(1005L);
        StpUtil.getSession().set("tenantId", "   ");
        assertNull(new SessionTenantResolver(null).resolve(request));
    }

    @Test
    @DisplayName("自定义属性名生效，缺省为 tenantId")
    void customAttributeNameIsUsed() {
        StpUtil.login(1006L);
        StpUtil.getSession().set("tenant", TENANT_ID);

        SessionTenantResolver resolver = new SessionTenantResolver("tenant", null);
        assertEquals("tenant", resolver.attribute());
        assertEquals(TENANT_ID, resolver.resolve(request));
        assertNull(new SessionTenantResolver(null).resolve(request));
    }

    @Test
    @DisplayName("属性名空白时回退默认值")
    void blankAttributeFallsBackToDefault() {
        StpUtil.login(1007L);
        StpUtil.getSession().set(SessionTenantResolver.DEFAULT_ATTRIBUTE, TENANT_ID);

        assertEquals("tenantId", new SessionTenantResolver("  ", null).attribute());
        assertEquals(TENANT_ID, new SessionTenantResolver("  ", null).resolve(request));
    }
}
