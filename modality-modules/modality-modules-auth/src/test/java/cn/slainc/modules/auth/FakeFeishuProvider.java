package cn.slainc.modules.auth;

import cn.slainc.thirdauth.ThirdPartyPlatform;
import cn.slainc.thirdauth.ThirdPartyUser;
import cn.slainc.thirdauth.provider.ThirdPartyAuthProvider;

public class FakeFeishuProvider implements ThirdPartyAuthProvider {

    private ThirdPartyUser nextUser;

    public void nextUser(ThirdPartyUser user) {
        this.nextUser = user;
    }

    @Override
    public ThirdPartyPlatform getPlatform() {
        return ThirdPartyPlatform.FEISHU;
    }

    @Override
    public String getAuthorizeUrl(String state, String redirectUri) {
        return "https://fake.feishu.example.com/open-apis/authen/v1/index?app_id=cli-key&state=" + state
                + "&redirect_uri=" + redirectUri;
    }

    @Override
    public ThirdPartyUser getUserInfo(String code, String state) {
        if (nextUser != null) {
            return nextUser;
        }
        ThirdPartyUser user = new ThirdPartyUser();
        user.setPlatform(ThirdPartyPlatform.FEISHU);
        user.setOpenId("open-default");
        user.setUnionId("union-default");
        user.setNickname("默认飞书用户");
        user.setAvatar("https://cdn.example.com/a.png");
        return user;
    }
}
