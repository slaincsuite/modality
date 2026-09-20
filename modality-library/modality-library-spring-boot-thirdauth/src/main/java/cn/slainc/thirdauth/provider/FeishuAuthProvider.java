package cn.slainc.thirdauth.provider;

import cn.slainc.thirdauth.ThirdPartyPlatform;
import cn.slainc.thirdauth.config.ThirdAuthConfigRegistry;
import me.zhyd.oauth.config.AuthConfig;
import me.zhyd.oauth.request.AuthFeishuRequest;
import me.zhyd.oauth.request.AuthRequest;

public class FeishuAuthProvider extends JustAuthProvider {

    public FeishuAuthProvider(ThirdAuthConfigRegistry configRegistry) {
        super(configRegistry);
    }

    @Override
    public ThirdPartyPlatform getPlatform() {
        return ThirdPartyPlatform.FEISHU;
    }

    @Override
    protected AuthRequest createRequest(AuthConfig config) {
        return new AuthFeishuRequest(config);
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
