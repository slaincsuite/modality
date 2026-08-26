package cn.slainc.tenant.helper;

import cn.slainc.core.context.TenantContext;

import java.util.function.Supplier;

public final class TenantHelper {

    private TenantHelper() {
    }

    public static void beginIgnore() {
        cn.slainc.mybatis.tenant.TenantHelper.beginIgnore();
    }

    public static void endIgnore() {
        cn.slainc.mybatis.tenant.TenantHelper.endIgnore();
    }

    public static boolean isIgnored() {
        return cn.slainc.mybatis.tenant.TenantHelper.isIgnored();
    }

    public static <T> T ignore(Supplier<T> supplier) {
        return cn.slainc.mybatis.tenant.TenantHelper.ignore(supplier);
    }

    public static void ignore(Runnable runnable) {
        cn.slainc.mybatis.tenant.TenantHelper.ignore(runnable);
    }

    public static <T> T callWith(Long tenantId, Supplier<T> supplier) {
        return TenantContext.callWith(tenantId, supplier);
    }

    public static void runWith(Long tenantId, Runnable runnable) {
        TenantContext.runWith(tenantId, runnable);
    }

    public static Long switchTo(Long tenantId) {
        Long previous = TenantContext.get();
        TenantContext.set(tenantId);
        return previous;
    }

    public static void restore(Long tenantId) {
        TenantContext.set(tenantId);
    }

    public static Long current() {
        return TenantContext.get();
    }

    public static Long currentOrDefault() {
        return TenantContext.getOrDefault();
    }

    public static Long require() {
        return TenantContext.require();
    }
}
