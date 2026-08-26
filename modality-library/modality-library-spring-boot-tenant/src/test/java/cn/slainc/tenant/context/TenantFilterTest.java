package cn.slainc.tenant.context;

import cn.slainc.core.context.TenantContext;
import cn.slainc.tenant.metrics.TenantMetricsRecorder;
import cn.slainc.tenant.resolver.TenantResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class TenantFilterTest {

    private final MockHttpServletRequest request = new MockHttpServletRequest();

    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("正常请求在链路中可见租户，链路结束后清理")
    void tenantVisibleInsideChainAndClearedAfterwards() throws Exception {
        RecordingMetrics metrics = new RecordingMetrics();
        TenantFilter filter = new TenantFilter(new FixedTenantResolver(7L), metrics);
        List<Long> seen = new ArrayList<>();
        filter.doFilter(request, response, (req, res) -> seen.add(TenantContext.get()));
        assertEquals(List.of(7L), seen);
        assertNull(TenantContext.get(), "请求结束后必须清理租户上下文");
        assertEquals(List.of(7L), metrics.resolved);
        assertEquals(0, metrics.missing);
    }

    @Test
    @DisplayName("链路抛出受检异常时仍清理租户上下文")
    void tenantClearedWhenChainThrowsServletException() {
        TenantFilter filter = new TenantFilter(new FixedTenantResolver(7L));
        assertThrows(ServletException.class, () -> filter.doFilter(request, response, (req, res) -> {
            assertEquals(7L, TenantContext.get());
            throw new ServletException("下游失败");
        }));
        assertNull(TenantContext.get(), "异常路径同样必须清理租户上下文");
    }

    @Test
    @DisplayName("链路抛出运行时异常时仍清理租户上下文")
    void tenantClearedWhenChainThrowsRuntimeException() {
        TenantFilter filter = new TenantFilter(new FixedTenantResolver(7L));
        assertThrows(IllegalStateException.class, () -> filter.doFilter(request, response, (req, res) -> {
            throw new IllegalStateException("下游失败");
        }));
        assertNull(TenantContext.get(), "异常路径同样必须清理租户上下文");
    }

    @Test
    @DisplayName("未解析出租户时不设置上下文并记录缺口指标")
    void noTenantResolvedLeavesContextEmpty() throws Exception {
        RecordingMetrics metrics = new RecordingMetrics();
        TenantFilter filter = new TenantFilter(new FixedTenantResolver(null), metrics);
        List<Long> seen = new ArrayList<>();
        filter.doFilter(request, response, (req, res) -> seen.add(TenantContext.get()));
        assertEquals(1, seen.size());
        assertNull(seen.get(0));
        assertNull(TenantContext.get());
        assertEquals(1, metrics.missing);
    }

    @Test
    @DisplayName("空解析器与非 HTTP 请求直接放行")
    void noResolverAndNonHttpRequestPassThrough() throws Exception {
        TenantFilter filter = new TenantFilter(null);
        List<String> invoked = new ArrayList<>();
        filter.doFilter(request, response, (req, res) -> invoked.add("http"));
        ServletRequest rawRequest = mock(ServletRequest.class);
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(rawRequest, response, chain);
        assertEquals(List.of("http"), invoked);
        assertNull(TenantContext.get());
    }

    private static final class FixedTenantResolver implements TenantResolver {

        private final Long tenantId;

        private FixedTenantResolver(Long tenantId) {
            this.tenantId = tenantId;
        }

        @Override
        public Long resolve(HttpServletRequest request) {
            return tenantId;
        }

        @Override
        public int order() {
            return 1;
        }
    }

    private static final class RecordingMetrics implements TenantMetricsRecorder {

        private final List<Long> resolved = new ArrayList<>();

        private int missing;

        @Override
        public void tenantResolved(Long tenantId) {
            resolved.add(tenantId);
        }

        @Override
        public void tenantMissing() {
            missing++;
        }
    }
}
