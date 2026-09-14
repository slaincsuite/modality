package cn.slainc.sso.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemorySsoClientRecordRepository implements SsoClientRecordRepository {

    private final Map<String, SsoClientRecord> store = new ConcurrentHashMap<>();

    private long sequence;

    @Override
    public Optional<SsoClientRecord> find(String client) {
        SsoClientRecord record = store.get(client);
        return Optional.ofNullable(record == null ? null : copy(record));
    }

    @Override
    public List<SsoClientRecord> list() {
        List<SsoClientRecord> result = new ArrayList<>();
        store.values().stream()
                .sorted(Comparator.comparing(SsoClientRecord::getCreatedAt))
                .forEach(record -> result.add(copy(record)));
        return result;
    }

    @Override
    public SsoClientRecord insert(SsoClientRecord record) {
        record.setId(++sequence);
        record.setCreatedAt(System.currentTimeMillis());
        record.setUpdatedAt(record.getCreatedAt());
        store.put(record.getClient(), copy(record));
        return record;
    }

    @Override
    public SsoClientRecord update(SsoClientRecord record) {
        record.setUpdatedAt(System.currentTimeMillis());
        store.put(record.getClient(), copy(record));
        return record;
    }

    @Override
    public void delete(String client) {
        store.remove(client);
    }

    private SsoClientRecord copy(SsoClientRecord source) {
        SsoClientRecord target = new SsoClientRecord();
        target.setId(source.getId());
        target.setClient(source.getClient());
        target.setClientName(source.getClientName());
        target.setSecretKey(source.getSecretKey());
        target.setAllowUrl(source.getAllowUrl());
        target.setBaseUrl(source.getBaseUrl());
        target.setPushUrl(source.getPushUrl());
        target.setPush(source.isPush());
        target.setSlo(source.isSlo());
        target.setStatus(source.getStatus());
        target.setCreatedAt(source.getCreatedAt());
        target.setUpdatedAt(source.getUpdatedAt());
        return target;
    }
}
