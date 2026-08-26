package cn.slainc.tenant.helper;

import cn.slainc.core.context.TenantContext;
import cn.slainc.core.context.UserContext;
import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

public final class TenantCrossAccess {

    private TenantCrossAccess() {
    }

    public static <T> T queryAs(Long tenantId, Supplier<T> supplier) {
        return TenantContext.callWith(tenantId, supplier);
    }

    public static void runAs(Long tenantId, Runnable runnable) {
        TenantContext.runWith(tenantId, runnable);
    }

    public static <T> Map<Long, T> forEachTenant(Collection<Long> tenantIds, Function<Long, T> function) {
        requireSuperAdmin();
        Map<Long, T> result = new LinkedHashMap<>();
        for (Long tenantId : distinct(tenantIds)) {
            result.put(tenantId, TenantContext.callWith(tenantId, () -> function.apply(tenantId)));
        }
        return result;
    }

    public static Map<Long, BigDecimal> sumByTenant(Collection<Long> tenantIds,
                                                    Function<Long, BigDecimal> function) {
        Map<Long, BigDecimal> result = new LinkedHashMap<>();
        forEachTenant(tenantIds, function).forEach((tenantId, value) ->
                result.put(tenantId, value == null ? BigDecimal.ZERO : value));
        return result;
    }

    public static BigDecimal total(Collection<Long> tenantIds, Function<Long, BigDecimal> function) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal value : sumByTenant(tenantIds, function).values()) {
            total = total.add(value);
        }
        return total;
    }

    private static Set<Long> distinct(Collection<Long> tenantIds) {
        Set<Long> result = new LinkedHashSet<>();
        if (tenantIds != null) {
            for (Long tenantId : tenantIds) {
                if (tenantId != null) {
                    result.add(tenantId);
                }
            }
        }
        return result;
    }

    private static void requireSuperAdmin() {
        if (!UserContext.isSuperAdmin()) {
            throw new BizException(ResultCode.FORBIDDEN, "跨租户统计仅限超级管理员");
        }
    }
}
