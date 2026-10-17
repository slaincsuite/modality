package cn.slainc.oauth2.client;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.oauth2.config.Oauth2Keys;
import org.springframework.beans.factory.ObjectProvider;

import java.net.URI;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Set;

public class ClientAdminService {

    private static final Set<String> KNOWN_GRANT_TYPES = Set.of(
            "authorization_code", "implicit", "password", "client_credentials", "refresh_token");

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ObjectProvider<ClientRecordRepository> repository;

    private final ClientRecordCache cache;

    private final ClientSecretCipher cipher;

    private final ObjectProvider<DynamicConfigService> configService;

    public ClientAdminService(ObjectProvider<ClientRecordRepository> repository, ClientRecordCache cache,
                              ClientSecretCipher cipher, ObjectProvider<DynamicConfigService> configService) {
        this.repository = repository;
        this.cache = cache;
        this.cipher = cipher;
        this.configService = configService;
    }

    public Oauth2ClientRecord register(Oauth2ClientRecord input) {
        validate(input);
        ClientRecordRepository store = requireStore();
        if (store.find(input.getClientId()).isPresent()) {
            throw new IllegalArgumentException("client_id 已存在：" + input.getClientId());
        }
        input.setStatus(Oauth2ClientRecord.STATUS_ENABLED);
        input.setClientSecret(cipher.encrypt(input.getClientSecret()));
        store.insert(input);
        afterChange(input.getClientId());
        return input;
    }

    public Oauth2ClientRecord update(Oauth2ClientRecord input) {
        ClientRecordRepository store = requireStore();
        Oauth2ClientRecord existing = store.find(input.getClientId())
                .orElseThrow(() -> new IllegalArgumentException("client_id 不存在：" + input.getClientId()));
        if (input.getClientSecret() != null && !input.getClientSecret().isBlank()) {
            existing.setClientSecret(cipher.encrypt(input.getClientSecret()));
            existing.setPreviousSecret(null);
            existing.setPreviousSecretExpireAt(null);
        }
        if (input.getClientName() != null) {
            existing.setClientName(input.getClientName());
        }
        if (input.getContractScopes() != null) {
            existing.setContractScopes(input.getContractScopes());
        }
        if (input.getAllowRedirectUris() != null) {
            existing.setAllowRedirectUris(input.getAllowRedirectUris());
        }
        if (input.getAllowGrantTypes() != null) {
            existing.setAllowGrantTypes(input.getAllowGrantTypes());
        }
        if (input.getSubjectId() != null) {
            existing.setSubjectId(input.getSubjectId());
        }
        if (input.getAccessTokenTimeout() > 0) {
            existing.setAccessTokenTimeout(input.getAccessTokenTimeout());
        }
        if (input.getRefreshTokenTimeout() > 0) {
            existing.setRefreshTokenTimeout(input.getRefreshTokenTimeout());
        }
        if (input.getClientTokenTimeout() > 0) {
            existing.setClientTokenTimeout(input.getClientTokenTimeout());
        }
        if (input.getMaxAccessTokenCount() >= 0) {
            existing.setMaxAccessTokenCount(input.getMaxAccessTokenCount());
        }
        if (input.getMaxRefreshTokenCount() >= 0) {
            existing.setMaxRefreshTokenCount(input.getMaxRefreshTokenCount());
        }
        if (input.getMaxClientTokenCount() >= 0) {
            existing.setMaxClientTokenCount(input.getMaxClientTokenCount());
        }
        existing.setNewRefresh(input.isNewRefresh());
        existing.setAutoConfirm(input.isAutoConfirm());
        if (input.getIpWhitelist() != null) {
            existing.setIpWhitelist(input.getIpWhitelist());
        }
        if (input.getQpsLimit() >= 0) {
            existing.setQpsLimit(input.getQpsLimit());
        }
        if (input.getStatus() != null) {
            existing.setStatus(input.getStatus());
        }
        if (input.getRemark() != null) {
            existing.setRemark(input.getRemark());
        }
        store.update(existing);
        afterChange(input.getClientId());
        return existing;
    }

    public void changeStatus(String clientId, boolean enabled) {
        Oauth2ClientRecord record = find(clientId);
        record.setStatus(enabled ? Oauth2ClientRecord.STATUS_ENABLED : Oauth2ClientRecord.STATUS_DISABLED);
        requireStore().update(record);
        afterChange(clientId);
    }

    public Oauth2ClientRecord rotateSecret(String clientId, long graceMillis) {
        Oauth2ClientRecord record = find(clientId);
        String plainOld = cipher.decrypt(record.getClientSecret());
        String plainNew = randomSecret();
        record.setPreviousSecret(cipher.encrypt(plainOld));
        record.setPreviousSecretExpireAt(System.currentTimeMillis() + Math.max(graceMillis, 0));
        record.setClientSecret(cipher.encrypt(plainNew));
        requireStore().update(record);
        afterChange(clientId);
        record.setClientSecret(plainNew);
        return record;
    }

    public void remove(String clientId) {
        requireStore().delete(clientId);
        afterChange(clientId);
    }

    public List<Oauth2ClientRecord> list() {
        return requireStore().list();
    }

    public Oauth2ClientRecord find(String clientId) {
        return requireStore().find(clientId)
                .orElseThrow(() -> new IllegalArgumentException("client_id 不存在：" + clientId));
    }

    public void refresh() {
        cache.evictAll();
    }

    private void afterChange(String clientId) {
        cache.evict(clientId);
        DynamicConfigService service = configService.getIfAvailable();
        if (service != null) {
            service.set(Oauth2Keys.CLIENT_REFRESH_KEY, String.valueOf(System.currentTimeMillis()), "oauth2");
        }
    }

    private ClientRecordRepository requireStore() {
        ClientRecordRepository store = repository.getIfAvailable();
        if (store == null) {
            throw new IllegalStateException("Client 存储仓库不可用，请检查 modality.oauth2.client.store 配置");
        }
        return store;
    }

    private void validate(Oauth2ClientRecord input) {
        if (input.getClientId() == null || !input.getClientId().matches("[A-Za-z0-9_-]{2,64}")) {
            throw new IllegalArgumentException("client_id 只允许 2-64 位字母、数字、下划线或中划线");
        }
        if (input.getClientSecret() == null || input.getClientSecret().isBlank()) {
            throw new IllegalArgumentException("client_secret 不能为空");
        }
        if (input.getAllowGrantTypes() != null) {
            for (String grantType : input.getAllowGrantTypes()) {
                if (!KNOWN_GRANT_TYPES.contains(grantType)) {
                    throw new IllegalArgumentException("不支持的授权类型：" + grantType);
                }
            }
        }
        if (input.getAllowRedirectUris() != null) {
            for (String uri : input.getAllowRedirectUris()) {
                try {
                    URI parsed = URI.create(uri);
                    String scheme = parsed.getScheme();
                    if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                        throw new IllegalArgumentException("redirect_uri 必须是 http/https 地址：" + uri);
                    }
                } catch (IllegalArgumentException e) {
                    if (e.getMessage() != null && e.getMessage().startsWith("redirect_uri")) {
                        throw e;
                    }
                    throw new IllegalArgumentException("redirect_uri 格式非法：" + uri);
                }
            }
        }
    }

    public static String randomSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
