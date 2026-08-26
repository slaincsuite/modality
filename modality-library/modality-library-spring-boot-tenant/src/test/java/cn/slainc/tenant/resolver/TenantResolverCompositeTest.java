package cn.slainc.tenant.resolver;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantResolverCompositeTest {

    private final MockHttpServletRequest request = new MockHttpServletRequest();

    @Test
    @DisplayName("按 order 依次尝试，第一个非空结果生效")
    void usesFirstNonNullResolution() {
        StubResolver late = new StubResolver(300, 3L, "late");
        StubResolver early = new StubResolver(100, null, "early");
        StubResolver middle = new StubResolver(200, 2L, "middle");
        TenantResolverComposite composite = new TenantResolverComposite(List.of(late, early, middle));
        assertEquals(List.of("early", "middle", "late"), composite.resolvers().stream()
                .map(resolver -> ((StubResolver) resolver).label).toList());
        assertEquals(2L, composite.resolve(request));
        assertEquals(List.of("early"), early.attempts);
        assertEquals(List.of("middle"), middle.attempts);
        assertTrue(late.attempts.isEmpty(), "已解析出租户后不再调用后续解析器");
    }

    @Test
    @DisplayName("全部解析器返回空时结果为 null")
    void returnsNullWhenAllResolversMiss() {
        TenantResolverComposite composite = new TenantResolverComposite(
                List.of(new StubResolver(100, null, "a"), new StubResolver(200, null, "b")));
        assertNull(composite.resolve(request));
        assertNull(composite.resolveWith(request));
    }

    @Test
    @DisplayName("组合器不包含自身，且能剔除空元素")
    void excludesItselfAndNullResolvers() {
        List<TenantResolver> resolvers = new ArrayList<>();
        resolvers.add(null);
        resolvers.add(new StubResolver(100, 1L, "only"));
        TenantResolverComposite composite = new TenantResolverComposite(resolvers);
        resolvers.add(composite);
        assertEquals(1, composite.resolvers().size());
        assertEquals(1L, composite.resolve(request));
        TenantResolverComposite nested = new TenantResolverComposite(List.of(composite));
        assertTrue(nested.resolvers().isEmpty(), "组合器不应嵌套自身");
    }

    @Test
    @DisplayName("resolveWith 返回命中的解析器")
    void resolveWithReturnsMatchedResolver() {
        StubResolver matched = new StubResolver(100, 8L, "matched");
        TenantResolverComposite composite = new TenantResolverComposite(
                List.of(new StubResolver(50, null, "before"), matched));
        TenantResolverComposite.TenantResolution resolution = composite.resolveWith(request);
        assertEquals(8L, resolution.tenantId());
        assertSame(matched, resolution.resolver());
    }

    private static final class StubResolver implements TenantResolver {

        private final int order;

        private final Long tenantId;

        private final String label;

        private final List<String> attempts = new ArrayList<>();

        private StubResolver(int order, Long tenantId, String label) {
            this.order = order;
            this.tenantId = tenantId;
            this.label = label;
        }

        @Override
        public Long resolve(HttpServletRequest request) {
            attempts.add(label);
            return tenantId;
        }

        @Override
        public int order() {
            return order;
        }
    }
}
