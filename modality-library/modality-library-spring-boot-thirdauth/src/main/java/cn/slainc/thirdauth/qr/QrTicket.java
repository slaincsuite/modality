package cn.slainc.thirdauth.qr;

import cn.slainc.thirdauth.ThirdPartyPlatform;

public class QrTicket {

    public enum Status {
        WAITING,
        // 仅在显式调用 markScanned 时出现，当前第三方回调链路直接置 CONFIRMED
        SCANNED,
        CONFIRMED,
        EXPIRED
    }

    private String ticketId;

    private ThirdPartyPlatform platform;

    private String status = Status.WAITING.name();

    private String payload;

    private long createdAt;

    private long expireAt;

    public boolean expired(long now) {
        return now >= expireAt;
    }

    public String getTicketId() {
        return ticketId;
    }

    public void setTicketId(String ticketId) {
        this.ticketId = ticketId;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
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
