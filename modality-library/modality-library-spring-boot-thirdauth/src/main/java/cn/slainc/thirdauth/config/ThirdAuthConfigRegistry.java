package cn.slainc.thirdauth.config;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.core.exception.ThirdPartyException;
import cn.slainc.thirdauth.ThirdPartyPlatform;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Optional;

public class ThirdAuthConfigRegistry {

    private final ThirdAuthProperties properties;

    private final ObjectProvider<DynamicConfigService> configService;

    public ThirdAuthConfigRegistry(ThirdAuthProperties properties,
                                   ObjectProvider<DynamicConfigService> configService) {
        this.properties = properties;
        this.configService = configService;
    }

    public Optional<ThirdAuthProperties.Platform> find(ThirdPartyPlatform platform) {
        ThirdAuthProperties.Platform base = properties.getPlatform().get(platform.code());
        if (base == null) {
            base = properties.getPlatform().get(platform.code().replace('_', '-'));
        }
        if (base == null) {
            return Optional.empty();
        }
        return Optional.of(overlay(platform, base));
    }

    public ThirdAuthProperties.Platform require(ThirdPartyPlatform platform) {
        return find(platform).orElseThrow(() -> new ThirdPartyException(
                platform.label() + "平台未配置，请在 modality.thirdauth.platform." + platform.code() + " 下补充 AppKey 等信息"));
    }

    public boolean enabled(ThirdPartyPlatform platform) {
        ThirdAuthProperties.Platform config = properties.getPlatform().get(platform.code());
        if (config == null) {
            config = properties.getPlatform().get(platform.code().replace('_', '-'));
        }
        if (config == null) {
            return false;
        }
        boolean overlayEnabled = overlayBoolean(platform, "enabled", config.isEnabled());
        return overlayEnabled && overlayString(platform, "app-key", config.getAppKey()) != null;
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
        merged.setAppKey(overlayString(platform, "app-key", base.getAppKey()));
        merged.setAppSecret(overlayString(platform, "app-secret", base.getAppSecret()));
        merged.setRedirectUri(overlayString(platform, "redirect-uri", base.getRedirectUri()));
        merged.setCallbackBase(overlayString(platform, "callback-base", base.getCallbackBase()));
        merged.setAgentId(overlayString(platform, "agent-id", base.getAgentId()));
        String scene = overlayString(platform, "scene", base.getScene() == null ? null : base.getScene().name());
        merged.setScene(scene == null || scene.isBlank() ? ThirdAuthProperties.Platform.Scene.WEB
                : ThirdAuthProperties.Platform.Scene.valueOf(scene.trim().toUpperCase()));
        return merged;
    }

    private String overlayString(ThirdPartyPlatform platform, String field, String defaultValue) {
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
        DynamicConfigService service = configService == null ? null : configService.getIfAvailable();
        if (service != null) {
            String key = ThirdAuthKeys.platformConfigKey(platform.code(), field);
            if (service.findString(key).isPresent()) {
                return service.getBoolean(key, defaultValue);
            }
        }
        return defaultValue;
    }
}
