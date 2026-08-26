package cn.slainc.tenant.resolver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DomainTenantResolverTest {

    private final MockHttpServletRequest request = new MockHttpServletRequest();

    private final TenantLookup lookup = Map.of("acme", 5L, "shop", 7L)::get;

    private final DomainTenantResolver resolver =
            new DomainTenantResolver("example.com", List.of("www", "api", "admin"), lookup);

    @Test
    @DisplayName("子域名解析为租户编码并转换为租户 ID")
    void resolvesSubdomain() {
        request.setServerName("acme.example.com");
        assertEquals(5L, resolver.resolve(request));
        assertEquals("example.com", resolver.rootDomain());
        assertEquals(DomainTenantResolver.ORDER, resolver.order());
    }

    @Test
    @DisplayName("多级子域名先整体匹配，失败后回退到首段")
    void resolvesMultiLevelSubdomain() {
        request.setServerName("shop.acme.example.com");
        assertEquals(7L, resolver.resolve(request));
    }

    @Test
    @DisplayName("保留子域名、根域名与裸 IP 不解析")
    void ignoresReservedAndRootDomain() {
        request.setServerName("www.example.com");
        assertNull(resolver.resolve(request));
        request.setServerName("api.example.com");
        assertNull(resolver.resolve(request));
        request.setServerName("example.com");
        assertNull(resolver.resolve(request));
        request.setServerName("localhost");
        assertNull(resolver.resolve(request));
        request.setServerName("127.0.0.1");
        assertNull(resolver.resolve(request));
        assertNull(resolver.resolve(null));
    }

    @Test
    @DisplayName("子域名是数字时按租户 ID 解析")
    void resolvesNumericSubdomain() {
        request.setServerName("9.example.com");
        assertEquals(9L, resolver.resolve(request));
    }

    @Test
    @DisplayName("未配置根域名时使用三段式主机的首段")
    void resolvesFirstLabelWithoutRootDomain() {
        DomainTenantResolver bare = new DomainTenantResolver(null, List.of("www"), lookup);
        request.setServerName("acme.example.com");
        assertEquals(5L, bare.resolve(request));
        request.setServerName("acme.local");
        assertNull(bare.resolve(request), "两段式主机不解析子域名");
    }

    @Test
    @DisplayName("主机名大小写不敏感")
    void resolvesHostCaseInsensitively() {
        request.setServerName("ACME.Example.com");
        assertEquals(5L, resolver.resolve(request));
    }
}
