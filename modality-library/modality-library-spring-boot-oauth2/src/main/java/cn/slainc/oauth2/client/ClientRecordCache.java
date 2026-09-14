package cn.slainc.oauth2.client;

import org.springframework.beans.factory.ObjectProvider;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ClientRecordCache {

    private static final long DEFAULT_TTL_MILLIS = 10_000;

    private final ObjectProvider<ClientRecordRepository> repository;

    private final long ttlMillis;

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private record CacheEntry(Oauth2ClientRecord record, long expireAt) {
    }

    public ClientRecordCache(ObjectProvider<ClientRecordRepository> repository, long cacheSeconds) {
        this.repository = repository;
        this.ttlMillis = cacheSeconds > 0 ? cacheSeconds * 1000 : DEFAULT_TTL_MILLIS;
    }

    public Optional<Oauth2ClientRecord> get(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return Optional.empty();
        }
        CacheEntry entry = cache.get(clientId);
        long now = System.currentTimeMillis();
        if (entry != null && entry.expireAt() > now) {
            return Optional.ofNullable(entry.record());
        }
        ClientRecordRepository store = repository.getIfAvailable();
        if (store == null) {
            return Optional.empty();
        }
        Optional<Oauth2ClientRecord> loaded = store.find(clientId);
        cache.put(clientId, new CacheEntry(loaded.orElse(null), now + ttlMillis));
        return loaded;
    }

    public void evict(String clientId) {
        if (clientId != null) {
            cache.remove(clientId);
        }
    }

    public void evictAll() {
        cache.clear();
    }
}
