package cn.slainc.modules.auth.mfa;

public class MfaLoginTicket {

    private String challengeId;

    private Long userId;

    private String username;

    private String loginType;

    private String deviceType;

    private String deviceId;

    private boolean remember;

    private long createdAt;

    private long expireAt;

    public MfaLoginTicket() {
    }

    public static MfaLoginTicket of(String challengeId, Long userId, String username, String loginType,
                                    String deviceType, String deviceId, boolean remember, long ttlSeconds) {
        MfaLoginTicket ticket = new MfaLoginTicket();
        ticket.challengeId = challengeId;
        ticket.userId = userId;
        ticket.username = username;
        ticket.loginType = loginType;
        ticket.deviceType = deviceType;
        ticket.deviceId = deviceId;
        ticket.remember = remember;
        ticket.createdAt = System.currentTimeMillis();
        ticket.expireAt = ticket.createdAt + ttlSeconds * 1000;
        return ticket;
    }

    public boolean expired(long now) {
        return now >= expireAt;
    }

    public String getChallengeId() {
        return challengeId;
    }

    public void setChallengeId(String challengeId) {
        this.challengeId = challengeId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getLoginType() {
        return loginType;
    }

    public void setLoginType(String loginType) {
        this.loginType = loginType;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public boolean isRemember() {
        return remember;
    }

    public void setRemember(boolean remember) {
        this.remember = remember;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(long expireAt) {
        this.expireAt = expireAt;
    }
}
