package cn.slainc.tenant.context;

import cn.slainc.core.context.TenantContext;
import cn.slainc.core.result.Result;
import cn.slainc.tenant.autoconfigure.TenantStatusCheckProperties;
import cn.slainc.tenant.entity.Tenant;
import cn.slainc.tenant.enums.TenantStatus;
import cn.slainc.tenant.metrics.TenantMetricsRecorder;
import cn.slainc.tenant.resolver.TenantResolver;
import cn.slainc.tenant.service.TenantService;
import cn.slainc.utils.json.JsonUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantStatusCheckFilterTest {

    private static final long TENANT_ID = 1001L;

    private static final int FORBIDDEN_CODE = 403000;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("试用租户未过期时放行并写入上下文")
    void trialTenantBeforeExpireIsAllowed() throws Exception {
        Fixture fixture = fixture(tenant(TenantStatus.TRIAL, LocalDateTime.now().plusDays(3)));
        fixture.filter.doFilter(fixture.request, fixture.response, fixture.chain);
        assertEquals(200, fixture.response.getStatus());
        assertTrue(fixture.chainInvoked(), "放行后应继续执行过滤器链");
        assertNull(TenantContext.get(), "请求结束后必须清理租户上下文");
        assertEquals(List.of(TENANT_ID), fixture.metrics.resolved);
        assertTrue(fixture.metrics.rejectedCodes.isEmpty());
    }

    @Test
    @DisplayName("试用租户超过 expireAt 时返回 403 统一 Result")
    void trialTenantAfterExpireIsRejected() throws Exception {
        assertRejected(fixture(tenant(TenantStatus.TRIAL, LocalDateTime.now().minusDays(1))), "租户已过期");
    }

    @Test
    @DisplayName("正常租户放行")
    void activeTenantIsAllowed() throws Exception {
        Fixture fixture = fixture(tenant(TenantStatus.ACTIVE, null));
        fixture.filter.doFilter(fixture.request, fixture.response, fixture.chain);
        assertEquals(200, fixture.response.getStatus());
        assertTrue(fixture.chainInvoked());
        assertNull(TenantContext.get());
        assertTrue(fixture.metrics.rejectedCodes.isEmpty());
    }

    @Test
    @DisplayName("已过期租户返回 403 统一 Result")
    void expiredTenantIsRejected() throws Exception {
        assertRejected(fixture(tenant(TenantStatus.EXPIRED, null)), "租户状态不允许访问");
    }

    @Test
    @DisplayName("已冻结租户返回 403 统一 Result")
    void frozenTenantIsRejected() throws Exception {
        Fixture fixture = fixture(tenant(TenantStatus.FROZEN, null));
        Map<String, Object> body = assertRejected(fixture, "租户状态不允许访问");
        assertTrue(String.valueOf(body.get("message")).contains("冻结"), String.valueOf(body.get("message")));
    }

    @Test
    @DisplayName("已停用租户返回 403 统一 Result")
    void disabledTenantIsRejected() throws Exception {
        Fixture fixture = fixture(tenant(TenantStatus.DISABLED, null));
        Map<String, Object> body = assertRejected(fixture, "租户状态不允许访问");
        assertTrue(String.valueOf(body.get("message")).contains("停用"), String.valueOf(body.get("message")));
    }

    @Test
    @DisplayName("查不到租户时默认拒绝并给出中文提示")
    void missingTenantIsRejectedByDefault() throws Exception {
        Map<String, Object> body = assertRejected(fixture(null), "租户不存在");
        assertTrue(String.valueOf(body.get("message")).contains(String.valueOf(TENANT_ID)),
                String.valueOf(body.get("message")));
    }

    @Test
    @DisplayName("查不到租户时可配置为放行")
    void missingTenantCanBeAllowed() throws Exception {
        TenantStatusCheckProperties properties = new TenantStatusCheckProperties();
        properties.setMissingTenantPolicy(TenantStatusCheckProperties.MissingTenantPolicy.ALLOW);
        Fixture fixture = fixture(null, properties);
        fixture.filter.doFilter(fixture.request, fixture.response, fixture.chain);
        assertEquals(200, fixture.response.getStatus());
        assertTrue(fixture.chainInvoked());
        assertNull(TenantContext.get());
    }

    @Test
    @DisplayName("白名单路径不校验租户状态，冻结租户也能登录")
    void allowedPathSkipsStatusCheck() throws Exception {
        Fixture fixture = fixture(tenant(TenantStatus.FROZEN, null));
        fixture.request.setRequestURI("/auth/login");
        fixture.filter.doFilter(fixture.request, fixture.response, fixture.chain);
        assertEquals(200, fixture.response.getStatus());
        assertTrue(fixture.chainInvoked());
        assertNull(TenantContext.get());
        verify(fixture.tenantService, times(0)).findById(anyLong());
    }

    @Test
    @DisplayName("健康检查路径放行")
    void actuatorHealthIsAllowed() throws Exception {
        Fixture fixture = fixture(tenant(TenantStatus.DISABLED, null));
        fixture.request.setRequestURI("/actuator/health");
        fixture.filter.doFilter(fixture.request, fixture.response, fixture.chain);
        assertEquals(200, fixture.response.getStatus());
        assertTrue(fixture.chainInvoked());
    }

    @Test
    @DisplayName("关闭状态拦截后任何状态都放行")
    void disabledStatusCheckLetEverythingPass() throws Exception {
        TenantStatusCheckProperties properties = new TenantStatusCheckProperties();
        properties.setEnabled(false);
        Fixture fixture = fixture(tenant(TenantStatus.FROZEN, null), properties);
        fixture.filter.doFilter(fixture.request, fixture.response, fixture.chain);
        assertEquals(200, fixture.response.getStatus());
        assertTrue(fixture.chainInvoked());
        verify(fixture.tenantService, times(0)).findById(anyLong());
    }

    @Test
    @DisplayName("同一租户连续请求只查一次库")
    void statusIsCachedPerTenant() throws Exception {
        Fixture fixture = fixture(tenant(TenantStatus.ACTIVE, null));
        fixture.filter.doFilter(fixture.request, fixture.response, fixture.chain);
        fixture.filter.doFilter(fixture.request, fixture.response, new MockFilterChain());
        verify(fixture.tenantService, times(1)).findById(TENANT_ID);
    }

    @Test
    @DisplayName("查询租户抛异常时按拒绝处理，不穿透原始异常")
    void lookupFailureIsRejectedWithoutLeakingException() throws Exception {
        TenantService tenantService = mock(TenantService.class);
        when(tenantService.findById(any())).thenThrow(new IllegalStateException("数据库不可用"));
        Fixture fixture = serviceFixture(tenantService, new TenantStatusCheckProperties());
        fixture.filter.doFilter(fixture.request, fixture.response, fixture.chain);
        assertEquals(403, fixture.response.getStatus());
        assertFalse(fixture.chainInvoked(), "校验异常时不得继续执行过滤器链");
        assertNull(TenantContext.get(), "异常路径同样必须清理租户上下文");
        Map<String, Object> body = JsonUtils.parseMap(fixture.response.getContentAsString());
        assertEquals(FORBIDDEN_CODE, body.get("code"));
        assertTrue(String.valueOf(body.get("message")).contains("校验失败"), String.valueOf(body.get("message")));
    }

    @Test
    @DisplayName("未携带租户标识且策略为拒绝时返回 403")
    void missingTenantIdentityIsRejected() throws Exception {
        TenantService tenantService = mock(TenantService.class);
        TenantFilter filter = new TenantFilter(new FixedTenantResolver(null), new RecordingMetrics(),
                new TenantStatusChecker(tenantService, new TenantStatusCheckProperties()));
        MockHttpServletResponse response = new MockHttpServletResponse();
        InvocationChain chain = new InvocationChain();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/orders"), response, chain);
        assertEquals(403, response.getStatus());
        assertFalse(chain.invoked, "拒绝时不得继续执行过滤器链");
        assertNull(TenantContext.get());
        Map<String, Object> body = JsonUtils.parseMap(response.getContentAsString());
        assertEquals(FORBIDDEN_CODE, body.get("code"));
        assertEquals("无法识别租户身份，请通过请求头携带租户标识后重试", body.get("message"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> assertRejected(Fixture fixture, String messageFragment) throws Exception {
        fixture.filter.doFilter(fixture.request, fixture.response, fixture.chain);
        assertEquals(403, fixture.response.getStatus(), "拒绝租户必须返回 HTTP 403");
        assertFalse(fixture.chainInvoked(), "拒绝时不得继续执行过滤器链");
        assertNull(TenantContext.get(), "拒绝路径同样必须清理租户上下文");
        Map<String, Object> body = JsonUtils.parseMap(fixture.response.getContentAsString());
        assertEquals(FORBIDDEN_CODE, body.get("code"), "拒绝必须返回统一错误码 403000");
        String message = String.valueOf(body.get("message"));
        assertTrue(message.contains(messageFragment), "文案应为中文且含「" + messageFragment + "」：" + message);
        assertNull(body.get("data"));
        assertTrue(((Number) body.get("timestamp")).longValue() > 0L);
        Result<Object> result = JsonUtils.parse(fixture.response.getContentAsString(), Result.class);
        assertFalse(result.isSuccess());
        assertEquals(403, result.httpStatus());
        assertEquals(List.of(FORBIDDEN_CODE), fixture.metrics.rejectedCodes);
        assertEquals(1, fixture.metrics.rejected);
        return body;
    }

    private Fixture fixture(Tenant tenant) {
        return fixture(tenant, new TenantStatusCheckProperties());
    }

    private Fixture fixture(Tenant tenant, TenantStatusCheckProperties properties) {
        TenantService tenantService = mock(TenantService.class);
        when(tenantService.findById(any())).thenReturn(tenant);
        return serviceFixture(tenantService, properties);
    }

    private Fixture serviceFixture(TenantService tenantService, TenantStatusCheckProperties properties) {
        RecordingMetrics metrics = new RecordingMetrics();
        TenantStatusChecker checker = new TenantStatusChecker(tenantService, properties);
        TenantFilter filter = new TenantFilter(new FixedTenantResolver(TENANT_ID), metrics, checker);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        request.setRequestURI("/api/orders");
        return new Fixture(filter, tenantService, request, new MockHttpServletResponse(), metrics);
    }

    private static Tenant tenant(TenantStatus status, LocalDateTime expireAt) {
        Tenant tenant = new Tenant();
        tenant.setId(TENANT_ID);
        tenant.setTenantCode("acme");
        tenant.setTenantName("试用租户");
        tenant.setStatus(status);
        tenant.setExpireAt(expireAt);
        return tenant;
    }

    private static final class Fixture {

        private final TenantFilter filter;

        private final TenantService tenantService;

        private final MockHttpServletRequest request;

        private final MockHttpServletResponse response;

        private final RecordingMetrics metrics;

        private final InvocationChain chain = new InvocationChain();

        private Fixture(TenantFilter filter, TenantService tenantService, MockHttpServletRequest request,
                        MockHttpServletResponse response, RecordingMetrics metrics) {
            this.filter = filter;
            this.tenantService = tenantService;
            this.request = request;
            this.response = response;
            this.metrics = metrics;
        }

        private boolean chainInvoked() {
            return chain.invoked;
        }
    }

    private static final class FixedTenantResolver implements TenantResolver {

        private final Long tenantId;

        private FixedTenantResolver(Long tenantId) {
            this.tenantId = tenantId;
        }

        @Override
        public Long resolve(jakarta.servlet.http.HttpServletRequest request) {
            return tenantId;
        }

        @Override
        public int order() {
            return 1;
        }
    }

    private static final class InvocationChain extends MockFilterChain {

        private boolean invoked;

        @Override
        public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
            invoked = true;
        }
    }

    private static final class RecordingMetrics implements TenantMetricsRecorder {

        private final List<Long> resolved = new ArrayList<>();

        private final List<Integer> rejectedCodes = new ArrayList<>();

        private int rejected;

        @Override
        public void tenantResolved(Long tenantId) {
            resolved.add(tenantId);
        }

        @Override
        public void tenantRejected(Long tenantId, int code) {
            rejected++;
            rejectedCodes.add(code);
        }
    }
}
