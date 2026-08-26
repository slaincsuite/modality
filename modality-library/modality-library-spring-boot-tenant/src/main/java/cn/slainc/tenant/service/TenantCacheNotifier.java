package cn.slainc.tenant.service;

@FunctionalInterface
public interface TenantCacheNotifier {

    TenantCacheNotifier NOOP = key -> {
    };

    void notify(String cacheKey);
}
