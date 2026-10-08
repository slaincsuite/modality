package cn.slainc.core.context;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;

import java.util.function.Supplier;

public final class TenantContext {

    public static final Long DEFAULT_TENANT_ID = 1L;

    public static final String SESSION_ATTRIBUTE = "tenantId";

    private static final ThreadLocal<Long> HOLDER = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(Long tenantId) {
        if (tenantId == null) {
            HOLDER.remove();
        } else {
            HOLDER.set(tenantId);
        }
    }

    public static Long get() {
        return HOLDER.get();
    }

    public static Long getOrDefault() {
        Long tenantId = HOLDER.get();
        return tenantId == null ? DEFAULT_TENANT_ID : tenantId;
    }

    public static Long require() {
        Long tenantId = HOLDER.get();
        if (tenantId == null) {
            throw new BizException(ResultCode.DATA_STATE_INVALID, "当前上下文缺少租户信息");
        }
        return tenantId;
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static <T> T callWith(Long tenantId, Supplier<T> supplier) {
        Long previous = HOLDER.get();
        set(tenantId);
        try {
            return supplier.get();
        } finally {
            set(previous);
        }
    }

    public static void runWith(Long tenantId, Runnable runnable) {
        Long previous = HOLDER.get();
        set(tenantId);
        try {
            runnable.run();
        } finally {
            set(previous);
        }
    }
}
