package cn.slainc.sso.client;

import cn.slainc.core.config.DynamicConfigService;
import org.springframework.beans.factory.ObjectProvider;

import java.net.URI;
import java.util.List;

public class SsoClientAdminService {

    public static final String CLIENT_REFRESH_KEY = "modality.sso.client.refresh";

    private final ObjectProvider<SsoClientRecordRepository> repository;

    private final SsoClientRecordCache cache;

    private final ObjectProvider<DynamicConfigService> configService;

    public SsoClientAdminService(ObjectProvider<SsoClientRecordRepository> repository,
                                 SsoClientRecordCache cache,
                                 ObjectProvider<DynamicConfigService> configService) {
        this.repository = repository;
        this.cache = cache;
        this.configService = configService;
    }

    public SsoClientRecord register(SsoClientRecord record) {
        validate(record);
        SsoClientRecordRepository store = requireStore();
        if (store.find(record.getClient()).isPresent()) {
            throw new IllegalArgumentException("client 已存在：" + record.getClient());
        }
        record.setStatus(SsoClientRecord.STATUS_ENABLED);
        store.insert(record);
        afterChange();
        return record;
    }

    public SsoClientRecord update(SsoClientRecord input) {
        SsoClientRecordRepository store = requireStore();
        SsoClientRecord existing = store.find(input.getClient())
                .orElseThrow(() -> new IllegalArgumentException("client 不存在：" + input.getClient()));
        if (input.getClientName() != null) {
            existing.setClientName(input.getClientName());
        }
        if (input.getSecretKey() != null && !input.getSecretKey().isBlank()) {
            existing.setSecretKey(input.getSecretKey());
        }
        if (input.getAllowUrl() != null) {
            existing.setAllowUrl(input.getAllowUrl());
        }
        if (input.getBaseUrl() != null) {
            existing.setBaseUrl(input.getBaseUrl());
        }
        if (input.getPushUrl() != null) {
            existing.setPushUrl(input.getPushUrl());
        }
        existing.setPush(input.isPush());
        existing.setSlo(input.isSlo());
        if (input.getStatus() != null) {
            existing.setStatus(input.getStatus());
        }
        store.update(existing);
        afterChange();
        return existing;
    }

    public void changeStatus(String client, boolean enabled) {
        SsoClientRecord record = find(client);
        record.setStatus(enabled ? SsoClientRecord.STATUS_ENABLED : SsoClientRecord.STATUS_DISABLED);
        requireStore().update(record);
        afterChange();
    }

    public void remove(String client) {
        requireStore().delete(client);
        afterChange();
    }

    public List<SsoClientRecord> list() {
        return requireStore().list();
    }

    public SsoClientRecord find(String client) {
        return requireStore().find(client)
                .orElseThrow(() -> new IllegalArgumentException("client 不存在：" + client));
    }

    public void refresh() {
        cache.evictAll();
    }

    private void afterChange() {
        cache.evictAll();
        DynamicConfigService service = configService.getIfAvailable();
        if (service != null) {
            service.set(CLIENT_REFRESH_KEY, String.valueOf(System.currentTimeMillis()), "sso");
        }
    }

    private void validate(SsoClientRecord record) {
        if (record.getClient() == null || !record.getClient().matches("[A-Za-z0-9_-]{2,64}")) {
            throw new IllegalArgumentException("client 标识只允许 2-64 位字母、数字、下划线或中划线");
        }
        if (record.getSecretKey() == null || record.getSecretKey().isBlank()) {
            throw new IllegalArgumentException("secret_key 不能为空");
        }
        for (String url : SsoClientRecord.splitAllowUrl(record.getAllowUrl())) {
            String scheme;
            try {
                scheme = URI.create(url).getScheme();
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("allow_url 格式非法：" + url);
            }
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new IllegalArgumentException("allow_url 必须是 http/https 地址：" + url);
            }
        }
    }

    private SsoClientRecordRepository requireStore() {
        SsoClientRecordRepository store = repository.getIfAvailable();
        if (store == null) {
            throw new IllegalStateException("SSO Client 存储仓库不可用");
        }
        return store;
    }
}
