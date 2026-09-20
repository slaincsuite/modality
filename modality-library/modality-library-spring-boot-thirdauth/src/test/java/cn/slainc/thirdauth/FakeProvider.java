package cn.slainc.thirdauth;

import cn.slainc.thirdauth.provider.ThirdPartyAuthProvider;

import java.util.List;
import java.util.Map;

public class FakeProvider implements ThirdPartyAuthProvider {

    private final ThirdPartyPlatform platform;

    private ThirdPartyUser nextUser;

    private String lastCode;

    private String lastState;

    public FakeProvider(ThirdPartyPlatform platform) {
        this.platform = platform;
    }

    public void nextUser(ThirdPartyUser user) {
        this.nextUser = user;
    }

    public String lastCode() {
        return lastCode;
    }

    public String lastState() {
        return lastState;
    }

    @Override
    public ThirdPartyPlatform getPlatform() {
        return platform;
    }

    @Override
    public String getAuthorizeUrl(String state, String redirectUri) {
        return "https://fake.example.com/oauth/authorize?client_id=fake&state=" + state
                + "&redirect_uri=" + redirectUri;
    }

    @Override
    public ThirdPartyUser getUserInfo(String code, String state) {
        this.lastCode = code;
        this.lastState = state;
        if (nextUser == null) {
            ThirdPartyUser user = new ThirdPartyUser();
            user.setPlatform(platform);
            user.setOpenId("open-" + platform.code());
            user.setNickname("测试用户");
            user.setAvatar("https://cdn.example.com/a.png");
            return user;
        }
        return nextUser;
    }

    public static ThirdPartyUser userOf(ThirdPartyPlatform platform, String openId, String unionId) {
        ThirdPartyUser user = new ThirdPartyUser();
        user.setPlatform(platform);
        user.setOpenId(openId);
        user.setUnionId(unionId);
        user.setNickname("昵称" + openId);
        user.setAvatar("https://cdn.example.com/" + openId + ".png");
        user.setRaw(Map.of("source", platform.code(), "scopes", List.of("basic")));
        ThirdPartyToken token = new ThirdPartyToken();
        token.setAccessToken("at-" + openId);
        token.setRefreshToken("rt-" + openId);
        token.setExpireIn(7200);
        user.setToken(token);
        return user;
    }
}
