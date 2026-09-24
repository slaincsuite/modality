package cn.slainc.modules.auth.qrcode;

public class QrLoginTicket {

    private String ticketId;

    private QrLoginStatus status = QrLoginStatus.WAITING;

    private Long loginId;

    private String username;

    private String deviceType;

    private String deviceId;

    private long createdAt;

    private long expireAt;

    public QrLoginTicket() {
    }

    public static QrLoginTicket waiting(String ticketId, long ttlSeconds) {
        QrLoginTicket ticket = new QrLoginTicket();
        ticket.ticketId = ticketId;
        ticket.status = QrLoginStatus.WAITING;
        ticket.createdAt = System.currentTimeMillis();
        ticket.expireAt = ticket.createdAt + ttlSeconds * 1000;
        return ticket;
    }

    public boolean expired(long now) {
        return now >= expireAt;
    }

    public long remainingSeconds(long now) {
        return Math.max(0, (expireAt - now) / 1000);
    }

    public String getTicketId() {
        return ticketId;
    }

    public void setTicketId(String ticketId) {
        this.ticketId = ticketId;
    }

    public QrLoginStatus getStatus() {
        return status;
    }

    public void setStatus(QrLoginStatus status) {
        this.status = status;
    }

    public Long getLoginId() {
        return loginId;
    }

    public void setLoginId(Long loginId) {
        this.loginId = loginId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
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
