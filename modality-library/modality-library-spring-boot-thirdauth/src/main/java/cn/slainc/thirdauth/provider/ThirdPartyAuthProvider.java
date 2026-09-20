package cn.slainc.thirdauth.provider;

import cn.slainc.thirdauth.ThirdPartyPlatform;
import cn.slainc.thirdauth.ThirdPartyUser;

public interface ThirdPartyAuthProvider {

    ThirdPartyPlatform getPlatform();

    String getAuthorizeUrl(String state, String redirectUri);

    ThirdPartyUser getUserInfo(String code, String state);

    default boolean supportsRefresh() {
        return false;
    }

    default ThirdPartyUser refresh(String refreshToken) {
        throw new IllegalArgumentException(getPlatform().label() + "平台不支持刷新访问令牌");
    }
}
