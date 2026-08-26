package cn.slainc.tenant.resolver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PathTenantResolverTest {

    private final MockHttpServletRequest request = new MockHttpServletRequest();

    private final TenantLookup lookup = Map.of("acme", 5L)::get;

    @Test
    @DisplayName("从 /api/{租户} 前缀解析租户编码")
    void resolvesTenantFromPrefix() {
        PathTenantResolver resolver = new PathTenantResolver("/api", 0, lookup);
        request.setRequestURI("/api/acme/user");
        assertEquals(5L, resolver.resolve(request));
        assertEquals("/api", resolver.prefix());
        assertEquals(0, resolver.index());
        assertEquals(PathTenantResolver.ORDER, resolver.order());
    }

    @Test
    @DisplayName("数字段直接作为租户 ID")
    void resolvesNumericSegment() {
        PathTenantResolver resolver = new PathTenantResolver("/api", 0, lookup);
        request.setRequestURI("/api/33/user");
        assertEquals(33L, resolver.resolve(request));
    }

    @Test
    @DisplayName("带上下文路径的请求先去掉上下文")
    void stripsContextPath() {
        PathTenantResolver resolver = new PathTenantResolver("/api", 0, lookup);
        request.setContextPath("/ctx");
        request.setRequestURI("/ctx/api/acme/user");
        assertEquals(5L, resolver.resolve(request));
    }

    @Test
    @DisplayName("前缀不匹配或段位不足时返回 null")
    void returnsNullWhenNotMatched() {
        PathTenantResolver resolver = new PathTenantResolver("/api", 0, lookup);
        request.setRequestURI("/other/acme/user");
        assertNull(resolver.resolve(request));
        request.setRequestURI("/api");
        assertNull(resolver.resolve(request));
        request.setRequestURI("/api/");
        assertNull(resolver.resolve(request));
        assertNull(resolver.resolve(null));
    }

    @Test
    @DisplayName("index 指定租户段的位置")
    void supportsSegmentIndex() {
        PathTenantResolver resolver = new PathTenantResolver("/api/v1", 0, lookup);
        request.setRequestURI("/api/v1/acme/user");
        assertEquals(5L, resolver.resolve(request));
        PathTenantResolver second = new PathTenantResolver("/api", 1, lookup);
        request.setRequestURI("/api/acme/shop");
        assertNull(second.resolve(request));
        assertEquals(1, second.index());
    }

    @Test
    @DisplayName("空前缀回退为默认 /api，负索引归零")
    void fallsBackToDefaults() {
        PathTenantResolver resolver = new PathTenantResolver("", -3, lookup);
        request.setRequestURI("/api/acme/user");
        assertEquals(5L, resolver.resolve(request));
        assertEquals("/api", resolver.prefix());
        assertEquals(0, resolver.index());
    }
}
