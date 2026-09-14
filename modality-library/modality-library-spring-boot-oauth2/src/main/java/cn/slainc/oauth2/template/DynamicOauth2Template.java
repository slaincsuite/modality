package cn.slainc.oauth2.template;

import cn.dev33.satoken.oauth2.data.model.loader.SaClientModel;
import cn.dev33.satoken.oauth2.error.SaOAuth2ErrorCode;
import cn.dev33.satoken.oauth2.exception.SaOAuth2ClientModelException;
import cn.dev33.satoken.oauth2.template.SaOAuth2Template;
import cn.dev33.satoken.util.SaFoxUtil;
import cn.slainc.oauth2.client.ClientRecordCache;
import cn.slainc.oauth2.client.ClientSecretCipher;
import cn.slainc.oauth2.client.Oauth2ClientRecord;
import cn.slainc.oauth2.consent.ConsentService;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Optional;

public class DynamicOauth2Template extends SaOAuth2Template {

    private final ClientRecordCache cache;

    private final ClientSecretCipher cipher;

    private final ObjectProvider<ConsentService> consentService;

    public DynamicOauth2Template(ClientRecordCache cache, ClientSecretCipher cipher,
                                 ObjectProvider<ConsentService> consentService) {
        this.cache = cache;
        this.cipher = cipher;
        this.consentService = consentService;
    }

    @Override
    public SaClientModel checkClientSecret(String clientId, String clientSecret) {
        SaClientModel cm = checkClientModel(clientId);
        if (cm.clientSecret != null && cm.clientSecret.equals(clientSecret)) {
            return cm;
        }
        Optional<Oauth2ClientRecord> record = cache.get(clientId);
        if (record.isPresent() && acceptsPreviousSecret(record.get(), clientSecret)) {
            return cm;
        }
        throw new SaOAuth2ClientModelException("无效 client_secret")
                .setClientId(clientId)
                .setCode(SaOAuth2ErrorCode.CODE_30115);
    }

    @Override
    public void saveGrantScope(String clientId, Object loginId, List<String> scopes) {
        super.saveGrantScope(clientId, loginId, scopes);
        ConsentService service = consentService.getIfAvailable();
        if (service != null) {
            service.record(loginId, clientId, scopes);
        }
    }

    // 默认实现里高风险 scope 每次都要确认，这里改为“记住选择”：已授权过即不再确认
    @Override
    public boolean isNeedCarefulConfirm(Object loginId, String clientId, List<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return false;
        }
        if (isGrantScope(loginId, clientId, scopes)) {
            return false;
        }
        if (SaFoxUtil.list1ContainList2AnyElement(scopes, getHigherScopeList())) {
            return true;
        }
        List<String> remaining = SaFoxUtil.list1RemoveByList2(scopes, getLowerScopeList());
        return !remaining.isEmpty();
    }

    private boolean acceptsPreviousSecret(Oauth2ClientRecord record, String clientSecret) {
        if (clientSecret == null || clientSecret.isBlank()) {
            return false;
        }
        Long expireAt = record.getPreviousSecretExpireAt();
        if (expireAt != null && expireAt > 0 && System.currentTimeMillis() > expireAt) {
            return false;
        }
        String previous = cipher.decrypt(record.getPreviousSecret());
        return previous != null && previous.equals(clientSecret);
    }
}
