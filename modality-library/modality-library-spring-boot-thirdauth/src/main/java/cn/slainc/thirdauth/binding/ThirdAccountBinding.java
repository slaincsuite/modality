package cn.slainc.thirdauth.binding;

import cn.slainc.thirdauth.ThirdPartyPlatform;

import java.security.SecureRandom;
import java.util.HexFormat;

public class ThirdAccountBinding {

    public enum Status {
        ACTIVE,
        UNBOUND
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    private Long id;

    private ThirdPartyPlatform platform;

    private String openId;

    private String unionId;

    private String userId;

    private String nickname;

    private String avatar;

    private long boundAt;

    private Long lastLoginAt;

    private String status = Status.ACTIVE.name();

    public static ThirdAccountBinding create(ThirdPartyPlatform platform, String openId, String unionId,
                                             String userId) {
        ThirdAccountBinding binding = new ThirdAccountBinding();
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        binding.setOpenId(openId);
        binding.setPlatform(platform);
        binding.setUnionId(unionId);
        binding.setUserId(userId);
        binding.setBoundAt(System.currentTimeMillis());
        return binding;
    }

    public boolean active() {
        return Status.ACTIVE.name().equals(status);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ThirdPartyPlatform getPlatform() {
        return platform;
    }

    public void setPlatform(ThirdPartyPlatform platform) {
        this.platform = platform;
    }

    public void setPlatform(String platform) {
        this.platform = ThirdPartyPlatform.of(platform);
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

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
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

    public long getBoundAt() {
        return boundAt;
    }

    public void setBoundAt(long boundAt) {
        this.boundAt = boundAt;
    }

    public Long getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(Long lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
