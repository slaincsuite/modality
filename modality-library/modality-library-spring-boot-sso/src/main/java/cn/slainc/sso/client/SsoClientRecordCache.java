package cn.slainc.sso.client;

import cn.slainc.sso.config.SsoProperties;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class SsoClientRecordCache {

    private final ObjectProvider<SsoClientRecordRepository> repository;

    private final SsoProperties properties;

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private record CacheEntry(List<SsoClientRecord> records, long expireAt) {
    }

    public SsoClientRecordCache(ObjectProvider<SsoClientRecordRepository> repository, SsoProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    public List<SsoClientRecord> listEnabled() {
        long ttlMillis = properties.getClientStore().getCacheSeconds() > 0
                ? properties.getClientStore().getCacheSeconds() * 1000 : 10_000;
        CacheEntry entry = cache.get("all");
        long now = System.currentTimeMillis();
        if (entry == null || entry.expireAt() <= now) {
            List<SsoClientRecord> loaded = load();
            cache.put("all", new CacheEntry(loaded, now + ttlMillis));
            return loaded;
        }
        return entry.records();
    }

    public Optional<SsoClientRecord> find(String client) {
        return listEnabled().stream()
                .filter(record -> record.getClient().equals(client))
                .findFirst();
    }

    public void evictAll() {
        cache.clear();
    }

    private List<SsoClientRecord> load() {
        SsoClientRecordRepository store = repository.getIfAvailable();
        List<SsoClientRecord> records = new ArrayList<>();
        if (store != null) {
            store.list().stream().filter(SsoClientRecord::enabled).forEach(records::add);
        }
        if (!records.isEmpty()) {
            return records;
        }
        for (SsoProperties.ClientItem item : properties.getClients()) {
            SsoClientRecord record = new SsoClientRecord();
            record.setClient(item.getClient());
            record.setClientName(item.getClient());
            record.setSecretKey(item.getSecretKey());
            record.setAllowUrl(item.getAllowUrl());
            record.setBaseUrl(item.getBaseUrl());
            record.setPushUrl(item.getPushUrl());
            record.setPush(item.isPush());
            record.setSlo(item.isSlo());
            record.setStatus(SsoClientRecord.STATUS_ENABLED);
            records.add(record);
        }
        return records;
    }
}
