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

    private ThirdAuthProperties propertiesWith(ThirdPartyPlatform platform, String appKey, String scene,
                                               String agentId) {
        ThirdAuthProperties properties = new ThirdAuthProperties();
        ThirdAuthProperties.Platform config = new ThirdAuthProperties.Platform();
        config.setEnabled(true);
        config.setAppKey(appKey);
        config.setAppSecret("secret-of-" + appKey);
        config.setCallbackBase("https://cb.example.com");
        if (scene != null) {
            config.setScene(ThirdAuthProperties.Platform.Scene.valueOf(scene));
        }
        config.setAgentId(agentId);
        properties.getPlatform().put(platform.code(), config);
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
    void wechatWebUrlPointsToMpAuthorizeEndpoint() {
        ThirdAuthConfigRegistry registry = new ThirdAuthConfigRegistry(
                propertiesWith(ThirdPartyPlatform.WECHAT, "wx_app_1", null, null), null);
        String url = new WechatAuthProvider(registry).getAuthorizeUrl("state-abc", null);
        URI uri = URI.create(url.replace("#wechat_redirect", ""));
        assertEquals("open.weixin.qq.com", uri.getHost());
        assertEquals("/connect/oauth2/authorize", uri.getPath());
        Map<String, String> params = queryParams(url);
        assertEquals("wx_app_1", params.get("appid"));
        assertEquals("state-abc", params.get("state"));
        assertEquals("https://cb.example.com/thirdauth/callback/wechat", params.get("redirect_uri"));
        assertTrue(url.contains("#wechat_redirect"));
    }

    @Test
    void wechatScanSceneSwitchesToOpenPlatformEndpoint() {
        ThirdAuthConfigRegistry registry = new ThirdAuthConfigRegistry(
                propertiesWith(ThirdPartyPlatform.WECHAT, "wx_open_1", "SCAN", null), null);
        String url = new WechatAuthProvider(registry).getAuthorizeUrl("state-scan", null);
        URI uri = URI.create(url.replace("#wechat_redirect", ""));
        assertEquals("open.weixin.qq.com", uri.getHost());
        assertTrue(uri.getPath().startsWith("/connect/qrconnect"));
        assertTrue(queryParams(url).containsKey("appid"));
    }

    @Test
    void wechatWorkWebUrlCarriesAgentId() {
        ThirdAuthConfigRegistry registry = new ThirdAuthConfigRegistry(
                propertiesWith(ThirdPartyPlatform.WECHAT_WORK, "corp_1", null, "1000002"), null);
        String url = new WechatWorkAuthProvider(registry).getAuthorizeUrl("state-w", null);
        Map<String, String> params = queryParams(url);
        assertEquals("corp_1", params.get("appid"));
        assertEquals("1000002", params.get("agentid"));
        assertEquals("state-w", params.get("state"));
    }

    @Test
    void wechatWorkScanSceneSwitchesToQrcodeEndpoint() {
        ThirdAuthConfigRegistry registry = new ThirdAuthConfigRegistry(
                propertiesWith(ThirdPartyPlatform.WECHAT_WORK, "corp_1", "SCAN", "1000002"), null);
        String url = new WechatWorkAuthProvider(registry).getAuthorizeUrl("state-q", null);
        assertEquals("open.work.weixin.qq.com", URI.create(url).getHost());
        assertTrue(url.contains("wwopen/sso"));
    }

    @Test
    void feishuUrlPointsToFeishuOpenPlatform() {
        ThirdAuthConfigRegistry registry = new ThirdAuthConfigRegistry(
                propertiesWith(ThirdPartyPlatform.FEISHU, "cli_feishu", null, null), null);
        String url = new FeishuAuthProvider(registry).getAuthorizeUrl("state-f", null);
        assertEquals("open.feishu.cn", URI.create(url).getHost());
        assertTrue(url.contains("/open-apis/authen/"));
        Map<String, String> params = queryParams(url);
        assertEquals("cli_feishu", params.get("app_id"));
        assertEquals("state-f", params.get("state"));
    }

    @Test
    void dingtalkUrlPointsToNewLoginEndpoint() {
        ThirdAuthConfigRegistry registry = new ThirdAuthConfigRegistry(
                propertiesWith(ThirdPartyPlatform.DINGTALK, "ding_key", null, null), null);
        String url = new DingtalkAuthProvider(registry).getAuthorizeUrl("state-d", null);
        assertEquals("login.dingtalk.com", URI.create(url).getHost());
        Map<String, String> params = queryParams(url);
        assertEquals("ding_key", params.getOrDefault("client_id", params.get("appid")));
        assertEquals("state-d", params.get("state"));
    }

    @Test
    void hotConfigAppKeyWithoutProviderRebuild() {
        FakeConfigService configService = new FakeConfigService();
        ThirdAuthProperties properties = propertiesWith(ThirdPartyPlatform.WECHAT, "wx_old", null, null);
        ThirdAuthConfigRegistry registry = new ThirdAuthConfigRegistry(properties, new SingleProvider<>(configService));
        WechatAuthProvider provider = new WechatAuthProvider(registry);
        assertTrue(provider.getAuthorizeUrl("s1", null).contains("wx_old"));
        configService.put("modality.thirdauth.platform.wechat.app-key", "wx_new_hot");
        assertTrue(provider.getAuthorizeUrl("s2", null).contains("wx_new_hot"));
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
