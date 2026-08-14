package cn.slainc.core.support;

public interface Cacheable {

    String cacheKey();

    default long cacheTtlSeconds() {
        return -1L;
    }
}
