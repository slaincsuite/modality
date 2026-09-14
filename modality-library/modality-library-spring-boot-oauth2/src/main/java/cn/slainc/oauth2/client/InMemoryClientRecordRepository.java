package cn.slainc.oauth2.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryClientRecordRepository implements ClientRecordRepository {

    private final Map<String, Oauth2ClientRecord> store = new ConcurrentHashMap<>();

    private final Map<String, Long> ids = new ConcurrentHashMap<>();

    private long sequence;

    @Override
    public Optional<Oauth2ClientRecord> find(String clientId) {
        Oauth2ClientRecord record = store.get(clientId);
        return Optional.ofNullable(record == null ? null : copy(record));
    }

    @Override
    public List<Oauth2ClientRecord> list() {
        List<Oauth2ClientRecord> result = new ArrayList<>();
        store.values().stream()
                .sorted(Comparator.comparing(Oauth2ClientRecord::getCreatedAt))
                .forEach(record -> result.add(copy(record)));
        return result;
    }

    @Override
    public Oauth2ClientRecord insert(Oauth2ClientRecord record) {
        long now = System.currentTimeMillis();
        record.setId(ids.computeIfAbsent(record.getClientId(), k -> ++sequence));
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        store.put(record.getClientId(), copy(record));
        return record;
    }

    @Override
    public Oauth2ClientRecord update(Oauth2ClientRecord record) {
        record.setUpdatedAt(System.currentTimeMillis());
        store.put(record.getClientId(), copy(record));
        return record;
    }

    @Override
    public void delete(String clientId) {
        store.remove(clientId);
    }

    private Oauth2ClientRecord copy(Oauth2ClientRecord source) {
        Oauth2ClientRecord target = new Oauth2ClientRecord();
        target.setId(source.getId());
        target.setClientId(source.getClientId());
        target.setClientSecret(source.getClientSecret());
        target.setPreviousSecret(source.getPreviousSecret());
        target.setPreviousSecretExpireAt(source.getPreviousSecretExpireAt());
        target.setClientName(source.getClientName());
        target.setContractScopes(new ArrayList<>(source.getContractScopes()));
        target.setAllowRedirectUris(new ArrayList<>(source.getAllowRedirectUris()));
        target.setAllowGrantTypes(new ArrayList<>(source.getAllowGrantTypes()));
        target.setSubjectId(source.getSubjectId());
        target.setAccessTokenTimeout(source.getAccessTokenTimeout());
        target.setRefreshTokenTimeout(source.getRefreshTokenTimeout());
        target.setClientTokenTimeout(source.getClientTokenTimeout());
        target.setMaxAccessTokenCount(source.getMaxAccessTokenCount());
        target.setMaxRefreshTokenCount(source.getMaxRefreshTokenCount());
        target.setMaxClientTokenCount(source.getMaxClientTokenCount());
        target.setNewRefresh(source.isNewRefresh());
        target.setAutoConfirm(source.isAutoConfirm());
        target.setIpWhitelist(new ArrayList<>(source.getIpWhitelist()));
        target.setQpsLimit(source.getQpsLimit());
        target.setStatus(source.getStatus());
        target.setRemark(source.getRemark());
        target.setCreatedAt(source.getCreatedAt());
        target.setUpdatedAt(source.getUpdatedAt());
        return target;
    }
}
