package cn.slainc.tenant.resolver;

import cn.slainc.core.constant.HttpHeaders;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HeaderTenantResolverTest {

    private final MockHttpServletRequest request = new MockHttpServletRequest();

    private final TenantLookup lookup = Map.of("acme", 5L)::get;

    @Test
    @DisplayName("默认读取 X-Tenant-Id 头的数字租户 ID")
    void resolvesNumericHeader() {
        HeaderTenantResolver resolver = new HeaderTenantResolver(lookup);
        request.addHeader(HttpHeaders.TENANT_ID, "12");
        assertEquals(12L, resolver.resolve(request));
        assertEquals(HttpHeaders.TENANT_ID, resolver.headerName());
        assertEquals(HeaderTenantResolver.ORDER, resolver.order());
    }

    @Test
    @DisplayName("请求头中的租户编码转换为租户 ID")
    void resolvesHeaderCode() {
        HeaderTenantResolver resolver = new HeaderTenantResolver(lookup);
        request.addHeader(HttpHeaders.TENANT_ID, "acme");
        assertEquals(5L, resolver.resolve(request));
    }

    @Test
    @DisplayName("缺少请求头或值非法时返回 null")
    void returnsNullWhenAbsentOrInvalid() {
        HeaderTenantResolver resolver = new HeaderTenantResolver(lookup);
        assertNull(resolver.resolve(request));
        request.addHeader(HttpHeaders.TENANT_ID, "  ");
        assertNull(resolver.resolve(request));
        request.addHeader(HttpHeaders.TENANT_ID, "unknown");
        assertNull(resolver.resolve(request));
        assertNull(resolver.resolve(null));
    }

    @Test
    @DisplayName("支持自定义请求头名称，空名称回退默认值")
    void supportsCustomHeaderName() {
        HeaderTenantResolver resolver = new HeaderTenantResolver("X-Custom-Tenant", lookup);
        request.addHeader("X-Custom-Tenant", "3");
        assertEquals(3L, resolver.resolve(request));
        assertEquals("X-Custom-Tenant", resolver.headerName());
        assertEquals(HttpHeaders.TENANT_ID, new HeaderTenantResolver("  ", lookup).headerName());
    }
}
