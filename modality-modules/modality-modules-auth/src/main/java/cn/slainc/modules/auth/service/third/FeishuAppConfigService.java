package cn.slainc.modules.auth.service.third;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;
import cn.slainc.modules.auth.dto.FeishuConfigRequest;
import cn.slainc.modules.auth.store.FeishuAppConfigStore;
import cn.slainc.modules.auth.support.ThirdSecretCodec;
import cn.slainc.modules.auth.third.FeishuAppConfig;
import cn.slainc.thirdauth.ThirdPartyPlatform;
import cn.slainc.thirdauth.config.ThirdAuthConfigProvider;
import cn.slainc.thirdauth.config.ThirdAuthProperties;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;

public class FeishuAppConfigService implements ThirdAuthConfigProvider {

    private static final String PLATFORM = ThirdPartyPlatform.FEISHU.code();

    private static final String MASK = "******";

    private final FeishuAppConfigStore store;

    private final ThirdSecretCodec secretCodec;

    private final ThirdAuthProperties properties;

    public FeishuAppConfigService(FeishuAppConfigStore store, ThirdSecretCodec secretCodec,
                                  ThirdAuthProperties properties) {
        this.store = store;
        this.secretCodec = secretCodec;
        this.properties = properties;
    }

    public record AppConfigView(String platform, String appId, String appSecret, String verificationToken,
                                String encryptKey, String callback, boolean enabled, boolean configured) {
    }

    public AppConfigView get() {
        FeishuAppConfig stored = store.find(PLATFORM);
        ThirdAuthProperties.Platform fallback = fallback();
        String appId = firstText(stored == null ? null : stored.getAppId(), fallback.getAppKey());
        String appSecretCipher = stored == null ? null : stored.getAppSecretCipher();
        String verificationCipher = stored == null ? null : stored.getVerificationTokenCipher();
        String encryptCipher = stored == null ? null : stored.getEncryptKeyCipher();
        String callback = firstText(stored == null ? null : stored.getCallbackUrl(), callbackOf(fallback));
        boolean enabled = stored != null ? Boolean.TRUE.equals(stored.getEnabled()) : fallback.isEnabled();
        boolean secretConfigured = hasText(appSecretCipher) || hasText(fallback.getAppSecret());
        return new AppConfigView(PLATFORM, appId, secretConfigured ? MASK : null,
                hasText(verificationCipher) ? MASK : null,
                hasText(encryptCipher) ? MASK : null,
                callback, enabled, hasText(appId) && secretConfigured);
    }

    public AppConfigView update(FeishuConfigRequest request) {
        if (request == null) {
            throw new BizException(ResultCode.PARAM_MISSING, "飞书应用配置不能为空");
        }
        FeishuAppConfig config = store.find(PLATFORM);
        if (config == null) {
            config = new FeishuAppConfig();
            config.setPlatform(PLATFORM);
            config.setEnabled(Boolean.FALSE);
        }
        if (request.appId() != null) {
            config.setAppId(limit(request.appId(), 128, "App ID"));
        }
        if (secretProvided(request.appSecret())) {
            config.setAppSecretCipher(secretCodec.encrypt(request.appSecret().trim()));
        }
        if (secretProvided(request.verificationToken())) {
            config.setVerificationTokenCipher(secretCodec.encrypt(request.verificationToken().trim()));
        }
        if (secretProvided(request.encryptKey())) {
            config.setEncryptKeyCipher(secretCodec.encrypt(request.encryptKey().trim()));
        }
        if (request.callback() != null) {
            config.setCallbackUrl(validateCallback(request.callback()));
        }
        store.save(config);
        return get();
    }

    public AppConfigView resetSecret() {
        FeishuAppConfig config = store.find(PLATFORM);
        if (config == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "飞书应用配置尚未创建");
        }
        config.setAppSecretCipher(null);
        config.setEnabled(Boolean.FALSE);
        store.save(config);
        return get();
    }

    public AppConfigView changeStatus(Boolean enabled) {
        if (enabled == null) {
            throw new BizException(ResultCode.PARAM_MISSING, "飞书联登状态不能为空");
        }
        FeishuAppConfig config = store.find(PLATFORM);
        if (config == null) {
            config = new FeishuAppConfig();
            config.setPlatform(PLATFORM);
        }
        if (enabled && !configured(config)) {
            throw new BizException(ResultCode.DATA_STATE_INVALID, "请先完整配置飞书 App ID 与 App Secret");
        }
        config.setEnabled(enabled);
        store.save(config);
        return get();
    }

    public String appId() {
        FeishuAppConfig config = store.find(PLATFORM);
        return firstText(config == null ? null : config.getAppId(), fallback().getAppKey());
    }

    public String appSecret() {
        FeishuAppConfig config = store.find(PLATFORM);
        if (config != null && hasText(config.getAppSecretCipher())) {
            return secretCodec.decrypt(config.getAppSecretCipher());
        }
        return fallback().getAppSecret();
    }

    @Override
    public Optional<String> find(ThirdPartyPlatform platform, String field) {
        if (platform != ThirdPartyPlatform.FEISHU) {
            return Optional.empty();
        }
        FeishuAppConfig config = store.find(PLATFORM);
        if (config == null) {
            return Optional.empty();
        }
        return switch (field) {
            case "enabled" -> Optional.of(String.valueOf(Boolean.TRUE.equals(config.getEnabled())));
            case "app-key" -> optionalText(config.getAppId());
            case "app-secret" -> hasText(config.getAppSecretCipher())
                    ? Optional.of(secretCodec.decrypt(config.getAppSecretCipher())) : Optional.empty();
            case "redirect-uri", "callback-base" -> optionalText(config.getCallbackUrl());
            default -> Optional.empty();
        };
    }

    private boolean configured(FeishuAppConfig config) {
        ThirdAuthProperties.Platform fallback = fallback();
        String appId = firstText(config.getAppId(), fallback.getAppKey());
        return hasText(appId) && (hasText(config.getAppSecretCipher()) || hasText(fallback.getAppSecret()));
    }

    private ThirdAuthProperties.Platform fallback() {
        ThirdAuthProperties.Platform config = properties.getPlatform().get(PLATFORM);
        return config == null ? new ThirdAuthProperties.Platform() : config;
    }

    private String callbackOf(ThirdAuthProperties.Platform fallback) {
        if (hasText(fallback.getRedirectUri())) {
            return fallback.getRedirectUri();
        }
        if (!hasText(fallback.getCallbackBase())) {
            return null;
        }
        String base = fallback.getCallbackBase().endsWith("/")
                ? fallback.getCallbackBase().substring(0, fallback.getCallbackBase().length() - 1)
                : fallback.getCallbackBase();
        return base + "/thirdauth/callback/" + PLATFORM;
    }

    private String validateCallback(String callback) {
        if (callback == null || callback.isBlank()) {
            return null;
        }
        String normalized = callback.trim();
        if (normalized.length() > 512) {
            throw new BizException(ResultCode.BAD_REQUEST, "飞书回调地址长度不能超过 512 个字符");
        }
        try {
            URI uri = URI.create(normalized);
            String scheme = uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!"https".equals(scheme) && !"http".equals(scheme)) {
                throw new IllegalArgumentException("scheme");
            }
            if (uri.getHost() == null) {
                throw new IllegalArgumentException("host");
            }
        } catch (IllegalArgumentException e) {
            throw new BizException(ResultCode.BAD_REQUEST, "飞书回调地址必须是完整的 HTTP 或 HTTPS 地址");
        }
        return normalized;
    }

    private String limit(String value, int maxLength, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new BizException(ResultCode.BAD_REQUEST, label + "长度不能超过 " + maxLength + " 个字符");
        }
        return normalized;
    }

    private boolean secretProvided(String value) {
        return value != null && !value.isBlank() && !value.contains("*");
    }

    private Optional<String> optionalText(String value) {
        return hasText(value) ? Optional.of(value) : Optional.empty();
    }

    private String firstText(String left, String right) {
        return hasText(left) ? left : right;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
