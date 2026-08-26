package cn.slainc.tenant.resolver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ParamTenantResolverTest {

    private final MockHttpServletRequest request = new MockHttpServletRequest();

    private final TenantLookup lookup = Map.of("acme", 5L)::get;

    @Test
    @DisplayName("默认读取 tenantId 参数")
    void resolvesDefaultParam() {
        ParamTenantResolver resolver = new ParamTenantResolver(lookup);
        request.addParameter("tenantId", "8");
        assertEquals(8L, resolver.resolve(request));
        assertEquals(ParamTenantResolver.DEFAULT_PARAM, resolver.paramName());
        assertEquals(ParamTenantResolver.ORDER, resolver.order());
    }

    @Test
    @DisplayName("参数为租户编码时转换为租户 ID")
    void resolvesParamCode() {
        ParamTenantResolver resolver = new ParamTenantResolver(lookup);
        request.addParameter("tenantId", "acme");
        assertEquals(5L, resolver.resolve(request));
    }

    @Test
    @DisplayName("支持自定义参数名，空名称回退默认值")
    void supportsCustomParamName() {
        ParamTenantResolver resolver = new ParamTenantResolver("tenant", lookup);
        request.addParameter("tenant", "6");
        assertEquals(6L, resolver.resolve(request));
        assertEquals("tenant", resolver.paramName());
        assertEquals(ParamTenantResolver.DEFAULT_PARAM, new ParamTenantResolver("", lookup).paramName());
    }

    @Test
    @DisplayName("缺少参数或参数非法时返回 null")
    void returnsNullWhenAbsent() {
        ParamTenantResolver resolver = new ParamTenantResolver(lookup);
        assertNull(resolver.resolve(request));
        request.addParameter("tenantId", "unknown");
        assertNull(resolver.resolve(request));
        assertNull(resolver.resolve(null));
    }
}
