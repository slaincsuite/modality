package cn.slainc.thirdauth.provider;

import cn.slainc.thirdauth.ThirdPartyPlatform;
import cn.slainc.thirdauth.config.ThirdAuthConfigRegistry;
import cn.slainc.thirdauth.config.ThirdAuthProperties;
import me.zhyd.oauth.config.AuthConfig;
import me.zhyd.oauth.request.AuthRequest;
import me.zhyd.oauth.request.AuthWeChatMpRequest;
import me.zhyd.oauth.request.AuthWeChatOpenRequest;

public class WechatAuthProvider extends JustAuthProvider {

    public WechatAuthProvider(ThirdAuthConfigRegistry configRegistry) {
        super(configRegistry);
    }

    @Override
    public ThirdPartyPlatform getPlatform() {
        return ThirdPartyPlatform.WECHAT;
    }

    @Override
    protected AuthRequest createRequest(AuthConfig config) {
        ThirdAuthProperties.Platform platformConfig = configRegistry.require(getPlatform());
        if (platformConfig.getScene() == ThirdAuthProperties.Platform.Scene.SCAN) {
            return new AuthWeChatOpenRequest(config);
        }
        return new AuthWeChatMpRequest(config);
    }

    @Override
    public boolean supportsRefresh() {
        return true;
    }

    @Override
    public cn.slainc.thirdauth.ThirdPartyUser refresh(String refreshToken) {
        return refreshByToken(refreshToken);
    }
}
