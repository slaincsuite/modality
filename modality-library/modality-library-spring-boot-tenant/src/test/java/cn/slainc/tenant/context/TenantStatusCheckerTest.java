package cn.slainc.tenant.context;

import cn.slainc.tenant.autoconfigure.TenantStatusCheckProperties;
import cn.slainc.tenant.domain.Tenant;
import cn.slainc.tenant.enums.TenantStatus;
import cn.slainc.tenant.lookup.TenantService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantStatusCheckerTest {

    private static final long TENANT_ID = 12L;

    private final TenantService tenantService = mock(TenantService.class);

    private final TenantStatusCheckProperties properties = new TenantStatusCheckProperties();

    private final TenantStatusChecker checker = new TenantStatusChecker(tenantService, properties);

    @Test
    @DisplayName("试用未过期放行，试用已过期拒绝")
    void trialDependsOnExpireAt() {
        assertTrue(checker.evaluate(tenant(TenantStatus.TRIAL, LocalDateTime.now().plusMinutes(1)), TENANT_ID)
                .allowed());
        TenantStatusCheckDecision expired = checker.evaluate(
                tenant(TenantStatus.TRIAL, LocalDateTime.now().minusMinutes(1)), TENANT_ID);
        assertFalse(expired.allowed());
        assertTrue(expired.message().contains("已过期"), expired.message());
        assertEquals(403, expired.httpStatus());
        assertEquals(403000, expired.errorCode().code());
    }

    @Test
    @DisplayName("试用恰好到点视为过期，到期时间对所有状态一视同仁")
    void boundaryAndActiveStatus() {
        LocalDateTime moment = LocalDateTime.now();
        assertFalse(checker.evaluate(tenant(TenantStatus.TRIAL, moment), TENANT_ID).allowed(),
                "expireAt 等于当前时刻应视为已过期");
        assertTrue(checker.evaluate(tenant(TenantStatus.ACTIVE, null), TENANT_ID).allowed(),
                "正常租户未设置到期时间时放行");
        assertTrue(checker.evaluate(tenant(TenantStatus.ACTIVE, moment.plusDays(1L)), TENANT_ID).allowed());
        // Tenant.isUsable 对任何状态都叠加到期判断，到期即拒绝，此处按既有实现断言
        TenantStatusCheckDecision stale = checker.evaluate(tenant(TenantStatus.ACTIVE, moment.minusDays(1L)),
                TENANT_ID);
        assertFalse(stale.allowed(), "已过期的正常租户同样拒绝");
        assertTrue(stale.message().contains("已过期"), stale.message());
    }

    @Test
    @DisplayName("过期、冻结、停用一律拒绝且文案含中文状态")
    void unusableStatusesAreRejected() {
        List<TenantStatus> statuses = List.of(TenantStatus.EXPIRED, TenantStatus.FROZEN, TenantStatus.DISABLED);
        for (TenantStatus status : statuses) {
            TenantStatusCheckDecision decision = checker.evaluate(tenant(status, null), TENANT_ID);
            assertFalse(decision.allowed(), status + " 应拒绝");
            assertEquals(403, decision.httpStatus());
            assertTrue(decision.message().contains(status.getDisplayName()), decision.message());
        }
    }

    @Test
    @DisplayName("状态为空视为不可用并拒绝")
    void nullStatusIsRejected() {
        Tenant tenant = tenant(TenantStatus.ACTIVE, null);
        tenant.setStatus(null);
        TenantStatusCheckDecision decision = checker.evaluate(tenant, TENANT_ID);
        assertFalse(decision.allowed());
        assertTrue(decision.message().contains("不允许访问"), decision.message());
    }

    @Test
    @DisplayName("租户名为空时用 ID 兜底")
    void blankTenantNameFallsBackToId() {
        Tenant tenant = tenant(TenantStatus.FROZEN, null);
        tenant.setTenantName("  ");
        assertTrue(checker.evaluate(tenant, TENANT_ID).message().contains(String.valueOf(TENANT_ID)));
    }

    @Test
    @DisplayName("传入空租户对象视为不存在")
    void nullTenantIsTreatedAsMissing() {
        TenantStatusCheckDecision decision = checker.evaluate(null, TENANT_ID);
        assertFalse(decision.allowed());
        assertTrue(decision.message().contains("租户不存在"), decision.message());
    }

    @Test
    @DisplayName("未装配 TenantService 时拒绝而不是抛异常")
    void missingTenantServiceIsRejected() {
        TenantStatusChecker withoutService = new TenantStatusChecker(null, properties);
        TenantStatusCheckDecision decision = withoutService.check("/api/orders", TENANT_ID);
        assertFalse(decision.allowed());
        assertEquals(403, decision.httpStatus());
        assertTrue(decision.message().contains("不可用"), decision.message());
    }

    @Test
    @DisplayName("未携带租户标识默认拒绝，可配置放行")
    void missingTenantIdentityFollowsPolicy() {
        TenantStatusCheckDecision rejected = checker.check("/api/orders", null);
        assertFalse(rejected.allowed());
        assertEquals(403, rejected.httpStatus());
        assertEquals(403000, rejected.errorCode().code());

        properties.setMissingTenantPolicy(TenantStatusCheckProperties.MissingTenantPolicy.ALLOW);
        assertTrue(checker.check("/api/orders", null).allowed());
    }

    @Test
    @DisplayName("查不到租户时按策略处理，不缓存空结果")
    void missingTenantFollowsPolicy() {
        when(tenantService.findById(any())).thenReturn(null);
        assertFalse(checker.check("/api/orders", TENANT_ID).allowed());

        properties.setMissingTenantPolicy(TenantStatusCheckProperties.MissingTenantPolicy.ALLOW);
        assertTrue(checker.check("/api/orders", TENANT_ID).allowed());
    }

    @Test
    @DisplayName("查询返回空时提示租户不存在")
    void nullLookupResultIsRejected() {
        when(tenantService.findById(anyLong())).thenReturn(null);
        TenantStatusCheckDecision decision = checker.check("/api/orders", TENANT_ID);
        assertFalse(decision.allowed());
        assertEquals(403000, decision.errorCode().code());
        assertTrue(decision.message().contains("租户不存在：" + TENANT_ID), decision.message());
    }

    @Test
    @DisplayName("缓存命中时不再查库，evict 后重新查库")
    void cacheIsUsedAndCanBeEvicted() {
        when(tenantService.findById(any())).thenReturn(tenant(TenantStatus.ACTIVE, null));
        checker.check("/api/orders", TENANT_ID);
        checker.check("/api/orders", TENANT_ID);
        verify(tenantService, times(1)).findById(TENANT_ID);

        checker.evict(TENANT_ID);
        checker.check("/api/orders", TENANT_ID);
        verify(tenantService, times(2)).findById(TENANT_ID);

        checker.evict(null);
        checker.evictAll();
        checker.check("/api/orders", TENANT_ID);
        verify(tenantService, times(3)).findById(TENANT_ID);
    }

    @Test
    @DisplayName("关闭开关或命中白名单时直接放行且不查库")
    void disabledOrAllowedPathSkipsLookup() {
        properties.setEnabled(false);
        assertTrue(checker.check("/api/orders", TENANT_ID).allowed());
        verify(tenantService, times(0)).findById(anyLong());

        properties.setEnabled(true);
        assertTrue(checker.check("/actuator/health", TENANT_ID).allowed());
        assertTrue(checker.check("/actuator/health/readiness", TENANT_ID).allowed());
        verify(tenantService, times(0)).findById(anyLong());
    }

    @Test
    @DisplayName("白名单支持 * 与 ** 通配，非法正则不影响放行判定")
    void allowedPathPatterns() {
        properties.setAllowedPaths(List.of("/auth/*", "/public/**", " ", "/broken/**/[", "/api/**"));
        assertTrue(checker.isAllowedPath("/auth/login"), "单星匹配一层");
        assertFalse(checker.isAllowedPath("/auth/login/extra"), "单星不跨层");
        assertTrue(checker.isAllowedPath("/public/any/deep/path"), "双星跨层");
        assertFalse(checker.isAllowedPath(null));
        assertFalse(checker.isAllowedPath(""));
        assertFalse(checker.isAllowedPath("   "));
        assertTrue(checker.isAllowedPath("/api/orders"), "双星匹配父路径本身");
        assertTrue(checker.isAllowedPath("/api/orders/1/items"));
        assertFalse(checker.isAllowedPath("/broken/anything"), "非法正则只忽略该条规则");
    }

    @Test
    @DisplayName("缓存有效期可配置")
    void cacheExpireIsConfigurable() {
        TenantStatusCheckProperties custom = new TenantStatusCheckProperties();
        custom.setCacheExpireAfterWrite(java.time.Duration.ofMillis(1L));
        custom.setCacheMaximumSize(1L);
        when(tenantService.findById(any())).thenReturn(tenant(TenantStatus.ACTIVE, null));

        TenantStatusChecker shortLived = new TenantStatusChecker(tenantService, custom);
        shortLived.check("/api/orders", TENANT_ID);
        try {
            Thread.sleep(20L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        shortLived.check("/api/orders", TENANT_ID);
        verify(tenantService, times(2)).findById(TENANT_ID);
    }

    private static Tenant tenant(TenantStatus status, LocalDateTime expireAt) {
        Tenant tenant = new Tenant();
        tenant.setId(TENANT_ID);
        tenant.setTenantName("示例租户");
        tenant.setStatus(status);
        tenant.setExpireAt(expireAt);
        return tenant;
    }
}
