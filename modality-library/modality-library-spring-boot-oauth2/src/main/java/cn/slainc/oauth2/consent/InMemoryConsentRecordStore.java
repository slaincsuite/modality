package cn.slainc.oauth2.consent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryConsentRecordStore implements ConsentRecordStore {

    private final Map<String, ConsentRecord> store = new ConcurrentHashMap<>();

    private long sequence;

    @Override
    public void upsert(String loginId, String clientId, List<String> scopes, long grantedAt) {
        ConsentRecord record = store.get(key(loginId, clientId));
        if (record == null) {
            record = new ConsentRecord();
            record.setId(++sequence);
            record.setLoginId(loginId);
            record.setClientId(clientId);
        }
        record.setScopes(scopes == null ? new ArrayList<>() : new ArrayList<>(scopes));
        record.setGrantedAt(grantedAt);
        record.setRevokedAt(null);
        store.put(key(loginId, clientId), record);
    }

    @Override
    public void markRevoked(String loginId, String clientId, long revokedAt) {
        ConsentRecord record = store.get(key(loginId, clientId));
        if (record != null && record.getRevokedAt() == null) {
            record.setRevokedAt(revokedAt);
        }
    }

    @Override
    public List<ConsentRecord> listByLoginId(String loginId) {
        return store.values().stream()
                .filter(record -> record.getLoginId().equals(loginId))
                .sorted(Comparator.comparingLong(ConsentRecord::getGrantedAt).reversed())
                .map(InMemoryConsentRecordStore::copy)
                .toList();
    }

    @Override
    public List<ConsentRecord> listByClientId(String clientId) {
        return store.values().stream()
                .filter(record -> record.getClientId().equals(clientId))
                .sorted(Comparator.comparingLong(ConsentRecord::getGrantedAt).reversed())
                .map(InMemoryConsentRecordStore::copy)
                .toList();
    }

    @Override
    public Optional<ConsentRecord> find(String loginId, String clientId) {
        ConsentRecord record = store.get(key(loginId, clientId));
        return Optional.ofNullable(record == null ? null : copy(record));
    }

    private static String key(String loginId, String clientId) {
        return loginId + "::" + clientId;
    }

    private static ConsentRecord copy(ConsentRecord source) {
        ConsentRecord target = new ConsentRecord();
        target.setId(source.getId());
        target.setLoginId(source.getLoginId());
        target.setClientId(source.getClientId());
        target.setScopes(new ArrayList<>(source.getScopes()));
        target.setGrantedAt(source.getGrantedAt());
        target.setRevokedAt(source.getRevokedAt());
        return target;
    }
}
