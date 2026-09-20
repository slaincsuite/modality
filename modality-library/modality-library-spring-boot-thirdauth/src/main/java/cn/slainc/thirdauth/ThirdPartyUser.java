package cn.slainc.thirdauth;

import java.util.LinkedHashMap;
import java.util.Map;

public class ThirdPartyUser {

    private ThirdPartyPlatform platform;

    private String openId;

    private String unionId;

    private String nickname;

    private String avatar;

    private ThirdPartyToken token;

    private Map<String, Object> raw = new LinkedHashMap<>();

    public ThirdPartyToken tokenOf() {
        return token == null ? new ThirdPartyToken() : token;
    }

    public ThirdPartyPlatform getPlatform() {
        return platform;
    }

    public void setPlatform(ThirdPartyPlatform platform) {
        this.platform = platform;
    }

    public String getOpenId() {
        return openId;
    }

    public void setOpenId(String openId) {
        this.openId = openId;
    }

    public String getUnionId() {
        return unionId;
    }

    public void setUnionId(String unionId) {
        this.unionId = unionId;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public ThirdPartyToken getToken() {
        return token;
    }

    public void setToken(ThirdPartyToken token) {
        this.token = token;
    }

    public Map<String, Object> getRaw() {
        return raw;
    }

    public void setRaw(Map<String, Object> raw) {
        this.raw = raw;
    }
}
