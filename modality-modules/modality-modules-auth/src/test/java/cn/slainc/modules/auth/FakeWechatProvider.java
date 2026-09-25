package cn.slainc.modules.auth;

import cn.slainc.thirdauth.ThirdPartyPlatform;
import cn.slainc.thirdauth.ThirdPartyUser;
import cn.slainc.thirdauth.provider.ThirdPartyAuthProvider;

public class FakeWechatProvider implements ThirdPartyAuthProvider {

    private ThirdPartyUser nextUser;

    public void nextUser(ThirdPartyUser user) {
        this.nextUser = user;
    }

    @Override
    public ThirdPartyPlatform getPlatform() {
        return ThirdPartyPlatform.WECHAT;
    }

    @Override
    public String getAuthorizeUrl(String state, String redirectUri) {
        return "https://fake.weixin.example.com/connect/qrconnect?appid=wx-key&state=" + state
                + "&redirect_uri=" + redirectUri;
    }

    @Override
    public ThirdPartyUser getUserInfo(String code, String state) {
        if (nextUser != null) {
            return nextUser;
        }
        ThirdPartyUser user = new ThirdPartyUser();
        user.setPlatform(ThirdPartyPlatform.WECHAT);
        user.setOpenId("open-default");
        user.setUnionId("union-default");
        user.setNickname("默认微信用户");
        user.setAvatar("https://cdn.example.com/a.png");
        return user;
    }
}
