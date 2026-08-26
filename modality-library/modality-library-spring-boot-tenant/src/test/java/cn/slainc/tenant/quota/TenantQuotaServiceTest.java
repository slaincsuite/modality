package cn.slainc.tenant.quota;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.core.exception.BizException;
import cn.slainc.tenant.metrics.TenantMetricsRecorder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TenantQuotaServiceTest {

    private final RecordingMetrics metrics = new RecordingMetrics();

    private final TenantQuotaService service =
            new TenantQuotaService(TenantQuota.of(100L, 0L, 0L, 0L), 0.8D, null, metrics);

    @Test
    @DisplayName("读取配额时合并默认值与租户维度")
    void readsQuotaWithDefaultValues() {
        TenantQuota quota = service.quotaOf(1L);
        assertEquals(1L, quota.getTenantId());
        assertEquals(100L, quota.getMaxUsers());
        assertEquals(0L, quota.getMaxStorage());
        assertEquals(100L, quota.limitOf(TenantQuotaDimension.USERS));
        assertEquals(0L, service.limitOf(1L, TenantQuotaDimension.API_DAILY));
    }

    @Test
    @DisplayName("未达阈值时通过检查")
    void passesBelowThreshold() {
        TenantQuotaCheckResult result = service.check(1L, TenantQuotaDimension.USERS, 50L);
        assertTrue(result.allowed());
        assertFalse(result.warning());
        assertEquals(100L, result.limit());
        assertEquals(50L, result.used());
        assertEquals(0.5D, result.usageRatio(), 0.0001D);
        assertNull(result.message());
        assertEquals(0, metrics.exceeded.size());
    }

    @Test
    @DisplayName("使用率达到 80% 时告警但放行")
    void warnsWhenReachingWarningRatio() {
        TenantQuotaCheckResult result = service.check(1L, TenantQuotaDimension.USERS, 80L);
        assertTrue(result.allowed());
        assertTrue(result.warning());
        assertTrue(result.message().contains("配额使用率已达 80%"), result.message());
        assertTrue(result.message().contains("请及时扩容"), result.message());
        assertDoesNotThrow(() -> service.require(1L, TenantQuotaDimension.USERS, 80L));
    }

    @Test
    @DisplayName("累加增量后超限时拒绝并抛出中文业务异常")
    void rejectsWhenExceedingLimit() {
        TenantQuotaCheckResult result = service.check(1L, TenantQuotaDimension.USERS, 99L, 2L);
        assertFalse(result.allowed());
        assertEquals(101L, result.requested());
        assertTrue(result.message().contains("配额已用尽"), result.message());
        assertEquals(List.of("USERS"), metrics.exceeded);

        BizException exception = assertThrows(BizException.class,
                () -> service.require(1L, TenantQuotaDimension.USERS, 99L, 2L));
        assertTrue(exception.getMessage().contains("已拒绝"), exception.getMessage());
    }

    @Test
    @DisplayName("配额为 0 表示不限制")
    void zeroLimitMeansUnlimited() {
        TenantQuotaCheckResult result = service.check(1L, TenantQuotaDimension.STORAGE, 999_999L);
        assertTrue(result.allowed());
        assertTrue(result.unlimited());
        assertDoesNotThrow(() -> service.require(1L, TenantQuotaDimension.STORAGE, 999_999L));
    }

    @Test
    @DisplayName("配额规则可由动态配置覆盖")
    void supportsDynamicConfigOverride() {
        DynamicConfigService configService = mock(DynamicConfigService.class);
        when(configService.getLong("tenant.quota.1.maxUsers", 100L)).thenReturn(30L);
        TenantQuotaService dynamic = new TenantQuotaService(TenantQuota.of(100L, 0L, 0L, 0L), 0.8D, configService,
                metrics);
        assertEquals(30L, dynamic.limitOf(1L, TenantQuotaDimension.USERS));
        assertTrue(dynamic.check(1L, TenantQuotaDimension.USERS, 25L).warning());
        assertFalse(dynamic.check(1L, TenantQuotaDimension.USERS, 31L).allowed());
    }

    private static final class RecordingMetrics implements TenantMetricsRecorder {

        private final List<String> exceeded = new ArrayList<>();

        @Override
        public void quotaExceeded(Long tenantId, String dimension) {
            exceeded.add(dimension);
        }
    }
}
