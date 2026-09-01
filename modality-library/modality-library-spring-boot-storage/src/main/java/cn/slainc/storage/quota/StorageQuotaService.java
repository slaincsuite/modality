package cn.slainc.storage.quota;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.storage.StorageException;
import cn.slainc.storage.model.QuotaUsage;
import cn.slainc.storage.support.StorageConfigKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class StorageQuotaService {

    private static final Logger log = LoggerFactory.getLogger(StorageQuotaService.class);

    private final Limits limits;

    private final DynamicConfigService configService;

    private final Map<String, AtomicLong> usage = new ConcurrentHashMap<>();

    public StorageQuotaService(Limits limits, DynamicConfigService configService) {
        this.limits = limits;
        this.configService = configService;
    }

    public void checkUpload(Long tenantId, Long userId, long size) {
        if (size <= 0) {
            return;
        }
        long tenantLimit = tenantLimit(tenantId);
        if (tenantLimit > 0) {
            long used = used("tenant", tenantId);
            if (used + size > tenantLimit) {
                throw new StorageException("租户存储配额不足，已用 " + used + " 字节，上限 " + tenantLimit
                        + " 字节，本次需要 " + size + " 字节");
            }
        }
        long userLimit = userLimit(userId);
        if (userLimit > 0) {
            long used = used("user", userId);
            if (used + size > userLimit) {
                throw new StorageException("用户存储配额不足，已用 " + used + " 字节，上限 " + userLimit
                        + " 字节，本次需要 " + size + " 字节");
            }
        }
    }

    public void record(Long tenantId, Long userId, long delta) {
        if (tenantId != null) {
            usage.computeIfAbsent(key("tenant", tenantId), name -> new AtomicLong()).addAndGet(delta);
        }
        if (userId != null) {
            usage.computeIfAbsent(key("user", userId), name -> new AtomicLong()).addAndGet(delta);
        }
        warnIfNeeded(tenantId, userId);
    }

    public QuotaUsage tenantUsage(Long tenantId) {
        return QuotaUsage.of(used("tenant", tenantId), tenantLimit(tenantId), limits.warningRatio());
    }

    public QuotaUsage userUsage(Long userId) {
        return QuotaUsage.of(used("user", userId), userLimit(userId), limits.warningRatio());
    }

    public long used(String scope, Long id) {
        if (id == null) {
            return 0L;
        }
        AtomicLong counter = usage.get(key(scope, id));
        return counter == null ? 0L : counter.get();
    }

    public void reset(String scope, Long id, long value) {
        usage.put(key(scope, id), new AtomicLong(value));
    }

    public long tenantLimit(Long tenantId) {
        if (tenantId == null) {
            return limits.defaultTenantLimit();
        }
        return dynamicLimit(StorageConfigKeys.TENANT_QUOTA_PREFIX + tenantId, limits.defaultTenantLimit());
    }

    public long userLimit(Long userId) {
        if (userId == null) {
            return limits.defaultUserLimit();
        }
        return dynamicLimit(StorageConfigKeys.USER_QUOTA_PREFIX + userId, limits.defaultUserLimit());
    }

    private long dynamicLimit(String key, long fallback) {
        if (configService == null) {
            return fallback;
        }
        long value = configService.getLong(key, fallback);
        return value <= 0 ? fallback : value;
    }

    private void warnIfNeeded(Long tenantId, Long userId) {
        QuotaUsage tenantUsage = tenantUsage(tenantId);
        if (tenantUsage.warning()) {
            log.warn("租户 {} 的存储用量已接近上限，已用 {} 字节 / 上限 {} 字节（{}%）", tenantId,
                    tenantUsage.used(), tenantUsage.limit(), Math.round(tenantUsage.ratio() * 100));
        }
        QuotaUsage userUsage = userUsage(userId);
        if (userUsage.warning()) {
            log.warn("用户 {} 的存储用量已接近上限，已用 {} 字节 / 上限 {} 字节（{}%）", userId,
                    userUsage.used(), userUsage.limit(), Math.round(userUsage.ratio() * 100));
        }
    }

    private static String key(String scope, Long id) {
        return scope + ":" + id;
    }

    public record Limits(long defaultTenantLimit, long defaultUserLimit, double warningRatio) {

        public static Limits of(long tenantLimit, long userLimit, double warningRatio) {
            return new Limits(tenantLimit, userLimit, warningRatio <= 0 ? 0.8d : warningRatio);
        }
    }
}
