package cn.slainc.tenant.quota;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;
import cn.slainc.tenant.metrics.TenantMetricsRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

public class TenantQuotaService {

    public static final String QUOTA_KEY_PREFIX = "tenant.quota.";

    private static final Logger log = LoggerFactory.getLogger(TenantQuotaService.class);

    private final TenantQuota defaults;

    private final double warningRatio;

    private final DynamicConfigService configService;

    private final TenantMetricsRecorder metrics;

    public TenantQuotaService(TenantQuota defaults, double warningRatio, DynamicConfigService configService,
                              TenantMetricsRecorder metrics) {
        this.defaults = defaults == null ? new TenantQuota() : defaults;
        this.warningRatio = warningRatio <= 0D || warningRatio >= 1D ? 0.8D : warningRatio;
        this.configService = configService;
        this.metrics = metrics == null ? TenantMetricsRecorder.NOOP : metrics;
    }

    public TenantQuota quotaOf(Long tenantId) {
        TenantQuota quota = new TenantQuota();
        quota.setTenantId(tenantId);
        for (TenantQuotaDimension dimension : TenantQuotaDimension.values()) {
            quota.limit(dimension, limitOf(tenantId, dimension));
        }
        return quota;
    }

    public long limitOf(Long tenantId, TenantQuotaDimension dimension) {
        long fallback = defaults.limitOf(dimension);
        if (configService == null || tenantId == null) {
            return fallback;
        }
        return configService.getLong(QUOTA_KEY_PREFIX + tenantId + "." + dimension.getProperty(), fallback);
    }

    public TenantQuotaCheckResult check(Long tenantId, TenantQuotaDimension dimension, long used) {
        return check(tenantId, dimension, used, 0L);
    }

    public TenantQuotaCheckResult check(Long tenantId, TenantQuotaDimension dimension, long used, long delta) {
        long limit = limitOf(tenantId, dimension);
        if (limit <= 0L) {
            return TenantQuotaCheckResult.unlimited(Math.max(used, 0L));
        }
        long current = Math.max(used, 0L);
        long requested = current + Math.max(delta, 0L);
        double usageRatio = (double) requested / (double) limit;
        if (requested > limit) {
            metrics.quotaExceeded(tenantId, dimension.name());
            return TenantQuotaCheckResult.exceeded(limit, current, requested, usageRatio,
                    "租户 [" + tenantId + "] 的" + dimension.getDisplayName() + "配额已用尽（" + current + "/" + limit
                            + "），本次需要 " + Math.max(delta, 0L) + "，已拒绝");
        }
        if (usageRatio >= warningRatio) {
            return TenantQuotaCheckResult.warning(limit, current, requested, usageRatio,
                    "租户 [" + tenantId + "] 的" + dimension.getDisplayName() + "配额使用率已达 "
                            + percent(usageRatio) + "（" + current + "/" + limit + "），请及时扩容");
        }
        return TenantQuotaCheckResult.passed(limit, current, requested, usageRatio);
    }

    public void require(Long tenantId, TenantQuotaDimension dimension, long used) {
        require(tenantId, dimension, used, 0L);
    }

    public void require(Long tenantId, TenantQuotaDimension dimension, long used, long delta) {
        TenantQuotaCheckResult result = check(tenantId, dimension, used, delta);
        if (result.warning()) {
            log.warn("{}", result.message());
        }
        if (!result.allowed()) {
            throw new BizException(ResultCode.BUSINESS_ERROR, result.message());
        }
    }

    private static String percent(double ratio) {
        return String.format(Locale.ROOT, "%.0f%%", ratio * 100);
    }
}
