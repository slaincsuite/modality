package cn.slainc.oauth2.consent;

import java.util.List;
import java.util.Optional;

public interface ConsentRecordStore {

    void upsert(String loginId, String clientId, List<String> scopes, long grantedAt);

    void markRevoked(String loginId, String clientId, long revokedAt);

    List<ConsentRecord> listByLoginId(String loginId);

    List<ConsentRecord> listByClientId(String clientId);

    Optional<ConsentRecord> find(String loginId, String clientId);
}
