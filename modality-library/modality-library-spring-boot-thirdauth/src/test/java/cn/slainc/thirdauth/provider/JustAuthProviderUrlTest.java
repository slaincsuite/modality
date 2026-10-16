package cn.slainc.thirdauth.provider;

import cn.slainc.thirdauth.FakeConfigService;
import cn.slainc.thirdauth.SingleProvider;
import cn.slainc.thirdauth.ThirdPartyPlatform;
import cn.slainc.thirdauth.config.ThirdAuthConfigRegistry;
import cn.slainc.thirdauth.config.ThirdAuthProperties;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JustAuthProviderUrlTest {

    private ThirdAuthProperties propertiesWith(String appKey) {
        ThirdAuthProperties properties = new ThirdAuthProperties();
        ThirdAuthProperties.Platform config = new ThirdAuthProperties.Platform();
        config.setEnabled(true);
        config.setAppKey(appKey);
        config.setAppSecret("secret-of-" + appKey);
        config.setCallbackBase("https://cb.example.com");
        properties.getPlatform().put(ThirdPartyPlatform.FEISHU.code(), config);
        return properties;
    }

    private Map<String, String> queryParams(String url) {
        Map<String, String> params = new HashMap<>();
        String query = URI.create(url).getRawQuery();
        for (String pair : query.split("&")) {
            int index = pair.indexOf('=');
            if (index > 0) {
                params.put(pair.substring(0, index),
                        URLDecoder.decode(pair.substring(index + 1), StandardCharsets.UTF_8));
            }
        }
        return params;
    }

    @Test
    void feishuUrlPointsToFeishuOpenPlatform() {
        ThirdAuthConfigRegistry registry = new ThirdAuthConfigRegistry(propertiesWith("cli_feishu"), null);
        String url = new FeishuAuthProvider(registry).getAuthorizeUrl("state-f", null);
        assertEquals("open.feishu.cn", URI.create(url).getHost());
        assertTrue(url.contains("/open-apis/authen/"));
        Map<String, String> params = queryParams(url);
        assertEquals("cli_feishu", params.get("app_id"));
        assertEquals("state-f", params.get("state"));
    }

    @Test
    void hotConfigAppKeyWithoutProviderRebuild() {
        FakeConfigService configService = new FakeConfigService();
        ThirdAuthConfigRegistry registry = new ThirdAuthConfigRegistry(propertiesWith("cli_old"),
                new SingleProvider<>(configService));
        FeishuAuthProvider provider = new FeishuAuthProvider(registry);
        assertTrue(provider.getAuthorizeUrl("s1", null).contains("cli_old"));
        configService.put("modality.thirdauth.platform.feishu.app-key", "cli_new_hot");
        assertTrue(provider.getAuthorizeUrl("s2", null).contains("cli_new_hot"));
    }

    @Test
    void unconfiguredPlatformGivesChineseError() {
        ThirdAuthConfigRegistry registry = new ThirdAuthConfigRegistry(new ThirdAuthProperties(), null);
        cn.slainc.core.exception.ThirdPartyException exception = org.junit.jupiter.api.Assertions.assertThrows(
                cn.slainc.core.exception.ThirdPartyException.class,
                () -> new FeishuAuthProvider(registry).getAuthorizeUrl("s", null));
        assertTrue(exception.getMessage().contains("飞书"));
        assertTrue(exception.getMessage().contains("平台未配置"));
    }
}
