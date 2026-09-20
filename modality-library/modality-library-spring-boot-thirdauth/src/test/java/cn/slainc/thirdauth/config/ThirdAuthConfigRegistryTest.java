package cn.slainc.thirdauth.config;

import cn.slainc.thirdauth.FakeConfigService;
import cn.slainc.thirdauth.SingleProvider;
import cn.slainc.thirdauth.ThirdPartyPlatform;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThirdAuthConfigRegistryTest {

    private ThirdAuthProperties.Platform platform(String appKey, String redirectUri, String callbackBase) {
        ThirdAuthProperties.Platform config = new ThirdAuthProperties.Platform();
        config.setEnabled(true);
        config.setAppKey(appKey);
        config.setAppSecret("secret");
        config.setRedirectUri(redirectUri);
        config.setCallbackBase(callbackBase);
        return config;
    }

    @Test
    void explicitRedirectUriTakesPrecedence() {
        ThirdAuthProperties properties = new ThirdAuthProperties();
        properties.getPlatform().put("feishu", platform("cli_1", "https://explicit.example.com/cb", null));
        properties.getPlatform().put("wechat", platform("wx_1", null, "https://base.example.com/"));
        ThirdAuthConfigRegistry registry = new ThirdAuthConfigRegistry(properties, null);

        assertEquals("https://explicit.example.com/cb", registry.redirectUriOf(ThirdPartyPlatform.FEISHU));
        assertEquals("https://base.example.com/thirdauth/callback/wechat",
                registry.redirectUriOf(ThirdPartyPlatform.WECHAT));
    }

    @Test
    void missingRedirectUriGivesChineseError() {
        ThirdAuthProperties properties = new ThirdAuthProperties();
        properties.getPlatform().put("feishu", platform("cli_1", null, null));
        ThirdAuthConfigRegistry registry = new ThirdAuthConfigRegistry(properties, null);
        cn.slainc.core.exception.ThirdPartyException exception = org.junit.jupiter.api.Assertions.assertThrows(
                cn.slainc.core.exception.ThirdPartyException.class,
                () -> registry.redirectUriOf(ThirdPartyPlatform.FEISHU));
        assertTrue(exception.getMessage().contains("未配置回调地址"));
    }

    @Test
    void hotConfigOverridesAppKeyAndScene() {
        FakeConfigService configService = new FakeConfigService();
        ThirdAuthProperties properties = new ThirdAuthProperties();
        properties.getPlatform().put("wechat", platform("wx_old", null, "https://base.example.com"));
        ThirdAuthConfigRegistry registry = new ThirdAuthConfigRegistry(properties, new SingleProvider<>(configService));

        assertEquals("wx_old", registry.find(ThirdPartyPlatform.WECHAT).orElseThrow().getAppKey());
        configService.put("modality.thirdauth.platform.wechat.app-key", "wx_hot");
        configService.put("modality.thirdauth.platform.wechat.scene", "SCAN");
        ThirdAuthProperties.Platform overlaid = registry.find(ThirdPartyPlatform.WECHAT).orElseThrow();
        assertEquals("wx_hot", overlaid.getAppKey());
        assertEquals(ThirdAuthProperties.Platform.Scene.SCAN, overlaid.getScene());
    }

    @Test
    void hotConfigCanDisablePlatform() {
        FakeConfigService configService = new FakeConfigService();
        ThirdAuthProperties properties = new ThirdAuthProperties();
        properties.getPlatform().put("dingtalk", platform("ding_1", null, "https://base.example.com"));
        ThirdAuthConfigRegistry registry = new ThirdAuthConfigRegistry(properties, new SingleProvider<>(configService));

        assertTrue(registry.enabled(ThirdPartyPlatform.DINGTALK));
        configService.put("modality.thirdauth.platform.dingtalk.enabled", "false");
        assertFalse(registry.enabled(ThirdPartyPlatform.DINGTALK));
    }

    @Test
    void unknownPlatformReturnsEmpty() {
        ThirdAuthConfigRegistry registry = new ThirdAuthConfigRegistry(new ThirdAuthProperties(), null);
        assertEquals(Optional.empty(), registry.find(ThirdPartyPlatform.DINGTALK));
    }
}
