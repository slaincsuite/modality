package cn.slainc.thirdauth.provider;

import cn.slainc.thirdauth.ThirdPartyPlatform;
import cn.slainc.thirdauth.config.ThirdAuthConfigRegistry;
import cn.slainc.thirdauth.config.ThirdAuthProperties;
import me.zhyd.oauth.config.AuthConfig;
import me.zhyd.oauth.request.AuthRequest;
import me.zhyd.oauth.request.AuthWeChatEnterpriseQrcodeRequest;
import me.zhyd.oauth.request.AuthWeChatEnterpriseWebRequest;

public class WechatWorkAuthProvider extends JustAuthProvider {

    public WechatWorkAuthProvider(ThirdAuthConfigRegistry configRegistry) {
        super(configRegistry);
    }

    @Override
    public ThirdPartyPlatform getPlatform() {
        return ThirdPartyPlatform.WECHAT_WORK;
    }

    @Override
    protected AuthRequest createRequest(AuthConfig config) {
        ThirdAuthProperties.Platform platformConfig = configRegistry.require(getPlatform());
        if (platformConfig.getScene() == ThirdAuthProperties.Platform.Scene.SCAN) {
            return new AuthWeChatEnterpriseQrcodeRequest(config);
        }
        return new AuthWeChatEnterpriseWebRequest(config);
    }
}
