package cn.slainc.mybatis.tenant;

import cn.slainc.core.context.TenantContext;

import java.util.function.Supplier;

public final class TenantHelper {

    private static final ThreadLocal<Integer> IGNORE_DEPTH = new ThreadLocal<>();

    private TenantHelper() {
    }

    public static void beginIgnore() {
        IGNORE_DEPTH.set(depth() + 1);
    }

    public static void endIgnore() {
        int current = depth() - 1;
        if (current <= 0) {
            IGNORE_DEPTH.remove();
        } else {
            IGNORE_DEPTH.set(current);
        }
    }

    public static boolean isIgnored() {
        return depth() > 0;
    }

    public static <T> T ignore(Supplier<T> supplier) {
        beginIgnore();
        try {
            return supplier.get();
        } finally {
            endIgnore();
        }
    }

    public static void ignore(Runnable runnable) {
        beginIgnore();
        try {
            runnable.run();
        } finally {
            endIgnore();
        }
    }

    public static <T> T callWith(Long tenantId, Supplier<T> supplier) {
        return TenantContext.callWith(tenantId, supplier);
    }

    private static int depth() {
        Integer value = IGNORE_DEPTH.get();
        return value == null ? 0 : value;
    }
}
