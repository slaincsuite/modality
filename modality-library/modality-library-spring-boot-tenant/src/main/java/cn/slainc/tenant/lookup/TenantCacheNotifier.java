package cn.slainc.tenant.lookup;

@FunctionalInterface
public interface TenantCacheNotifier {

    TenantCacheNotifier NOOP = key -> {
    };

    void notify(String cacheKey);
}
