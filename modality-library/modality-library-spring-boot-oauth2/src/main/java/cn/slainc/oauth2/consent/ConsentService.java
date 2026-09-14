package cn.slainc.oauth2.consent;

import cn.dev33.satoken.oauth2.template.SaOAuth2Template;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ConsentService {

    private final ObjectProvider<ConsentRecordStore> store;

    private final ObjectProvider<SaOAuth2Template> template;

    public ConsentService(ObjectProvider<ConsentRecordStore> store, ObjectProvider<SaOAuth2Template> template) {
        this.store = store;
        this.template = template;
    }

    public void record(Object loginId, String clientId, List<String> scopes) {
        ConsentRecordStore recordStore = requireStore();
        long now = System.currentTimeMillis();
        recordStore.upsert(String.valueOf(loginId), clientId, scopes, now);
    }

    public void grantAndRecord(Object loginId, String clientId, List<String> scopes) {
        SaOAuth2Template oauth2Template = template.getIfAvailable();
        if (oauth2Template != null) {
            oauth2Template.saveGrantScope(clientId, loginId, scopes);
        }
        record(loginId, clientId, scopes);
    }

    public void revoke(Object loginId, String clientId) {
        ConsentRecordStore recordStore = requireStore();
        recordStore.markRevoked(String.valueOf(loginId), clientId, System.currentTimeMillis());
        SaOAuth2Template oauth2Template = template.getIfAvailable();
        if (oauth2Template != null) {
            oauth2Template.deleteGrantScope(loginId, clientId);
        }
    }

    public List<ConsentRecord> listByUser(String loginId) {
        return requireStore().listByLoginId(String.valueOf(loginId));
    }

    public List<ConsentRecord> listByClient(String clientId) {
        return requireStore().listByClientId(clientId);
    }

    public Map<String, List<String>> activeScopesByClient(String loginId) {
        return listByUser(loginId).stream()
                .filter(ConsentRecord::active)
                .collect(Collectors.toMap(
                        ConsentRecord::getClientId,
                        ConsentRecord::getScopes,
                        (left, right) -> right));
    }

    private ConsentRecordStore requireStore() {
        ConsentRecordStore recordStore = store.getIfAvailable();
        if (recordStore == null) {
            throw new IllegalStateException("授权记录存储不可用");
        }
        return recordStore;
    }
}
