package cn.slainc.thirdauth.provider;

import cn.slainc.thirdauth.ThirdPartyPlatform;
import cn.slainc.thirdauth.config.ThirdAuthConfigRegistry;
import me.zhyd.oauth.config.AuthConfig;
import me.zhyd.oauth.request.AuthDingTalkV2Request;
import me.zhyd.oauth.request.AuthRequest;

public class DingtalkAuthProvider extends JustAuthProvider {

    public DingtalkAuthProvider(ThirdAuthConfigRegistry configRegistry) {
        super(configRegistry);
    }

    @Override
    public ThirdPartyPlatform getPlatform() {
        return ThirdPartyPlatform.DINGTALK;
    }

    @Override
    protected AuthRequest createRequest(AuthConfig config) {
        return new AuthDingTalkV2Request(config);
    }
}
