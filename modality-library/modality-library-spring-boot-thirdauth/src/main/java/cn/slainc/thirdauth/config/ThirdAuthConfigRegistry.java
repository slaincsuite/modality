package cn.slainc.thirdauth.config;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.core.exception.ThirdPartyException;
import cn.slainc.thirdauth.ThirdPartyPlatform;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Optional;

public class ThirdAuthConfigRegistry {

    private final ThirdAuthProperties properties;

    private final ObjectProvider<DynamicConfigService> configService;

    private final ObjectProvider<ThirdAuthConfigProvider> configProvider;

    public ThirdAuthConfigRegistry(ThirdAuthProperties properties,
                                   ObjectProvider<DynamicConfigService> configService) {
        this(properties, configService, null);
    }

    public ThirdAuthConfigRegistry(ThirdAuthProperties properties,
                                   ObjectProvider<DynamicConfigService> configService,
                                   ObjectProvider<ThirdAuthConfigProvider> configProvider) {
        this.properties = properties;
        this.configService = configService;
        this.configProvider = configProvider;
    }

    public Optional<ThirdAuthProperties.Platform> find(ThirdPartyPlatform platform) {
        ThirdAuthProperties.Platform base = properties.getPlatform().get(platform.code());
        if (base == null) {
            base = properties.getPlatform().get(platform.code().replace('_', '-'));
        }
        ThirdAuthProperties.Platform merged = overlay(platform,
                base == null ? new ThirdAuthProperties.Platform() : base);
        if (!hasConfiguration(merged)) {
            return Optional.empty();
        }
        return Optional.of(merged);
    }

    public ThirdAuthProperties.Platform require(ThirdPartyPlatform platform) {
        return find(platform).orElseThrow(() -> new ThirdPartyException(
                platform.label() + "平台未配置，请在 modality.thirdauth.platform." + platform.code() + " 下补充 AppKey 等信息"));
    }

    public boolean enabled(ThirdPartyPlatform platform) {
        ThirdAuthProperties.Platform base = properties.getPlatform().get(platform.code());
        if (base == null) {
            base = properties.getPlatform().get(platform.code().replace('_', '-'));
        }
        ThirdAuthProperties.Platform merged = overlay(platform,
                base == null ? new ThirdAuthProperties.Platform() : base);
        return merged.isEnabled() && hasText(merged.getAppKey()) && hasText(merged.getAppSecret());
    }

    public long requestTimeoutSeconds() {
        return Math.max(1, properties.getRequestTimeoutSeconds());
    }

    public String redirectUriOf(ThirdPartyPlatform platform) {
        ThirdAuthProperties.Platform config = require(platform);
        String configured = overlayString(platform, "redirect-uri", config.getRedirectUri());
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        String callbackBase = overlayString(platform, "callback-base", config.getCallbackBase());
        if (callbackBase == null || callbackBase.isBlank()) {
            throw new ThirdPartyException(platform.label() + "平台未配置回调地址，请设置 redirect-uri 或 callback-base");
        }
        String normalized = callbackBase.endsWith("/") ? callbackBase.substring(0, callbackBase.length() - 1)
                : callbackBase;
        return normalized + "/thirdauth/callback/" + platform.code();
    }

    private ThirdAuthProperties.Platform overlay(ThirdPartyPlatform platform, ThirdAuthProperties.Platform base) {
        ThirdAuthProperties.Platform merged = new ThirdAuthProperties.Platform();
        merged.setEnabled(overlayBoolean(platform, "enabled", base.isEnabled()));
        merged.setSuspended(overlayBoolean(platform, "suspended", base.isSuspended()));
        merged.setAppKey(overlayString(platform, "app-key", base.getAppKey()));
        merged.setAppSecret(overlayString(platform, "app-secret", base.getAppSecret()));
        merged.setRedirectUri(overlayString(platform, "redirect-uri", base.getRedirectUri()));
        merged.setCallbackBase(overlayString(platform, "callback-base", base.getCallbackBase()));
        return merged;
    }

    private String overlayString(ThirdPartyPlatform platform, String field, String defaultValue) {
        ThirdAuthConfigProvider provider = configProvider == null ? null : configProvider.getIfAvailable();
        if (provider != null) {
            String value = provider.find(platform, field).orElse(null);
            if (value != null) {
                return value;
            }
        }
        DynamicConfigService service = configService == null ? null : configService.getIfAvailable();
        if (service != null) {
            String key = ThirdAuthKeys.platformConfigKey(platform.code(), field);
            String value = service.getString(key, null);
            if (value != null) {
                return value;
            }
        }
        return defaultValue;
    }

    private boolean overlayBoolean(ThirdPartyPlatform platform, String field, boolean defaultValue) {
        String value = overlayString(platform, field, null);
        if (value != null && !value.isBlank()) {
            return Boolean.parseBoolean(value);
        }
        return defaultValue;
    }

    private boolean hasConfiguration(ThirdAuthProperties.Platform platform) {
        return platform.isEnabled() || hasText(platform.getAppKey()) || hasText(platform.getAppSecret())
                || hasText(platform.getRedirectUri()) || hasText(platform.getCallbackBase());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
