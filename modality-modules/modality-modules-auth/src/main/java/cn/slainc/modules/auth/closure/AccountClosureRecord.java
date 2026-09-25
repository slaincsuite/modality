package cn.slainc.modules.auth.closure;

public class AccountClosureRecord {

    public static final String STATUS_PENDING = "PENDING";

    public static final String STATUS_CANCELLED = "CANCELLED";

    public static final String STATUS_EXECUTED = "EXECUTED";

    public static final String STATUS_FAILED = "FAILED";

    private Long id;

    private Long tenantId;

    private Long userId;

    private String loginType;

    private String username;

    private String reason;

    private String status = STATUS_PENDING;

    private long requestedAt;

    private long coolDownUntil;

    private Long executedAt;

    private String executeError;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getLoginType() {
        return loginType;
    }

    public void setLoginType(String loginType) {
        this.loginType = loginType;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(long requestedAt) {
        this.requestedAt = requestedAt;
    }

    public long getCoolDownUntil() {
        return coolDownUntil;
    }

    public void setCoolDownUntil(long coolDownUntil) {
        this.coolDownUntil = coolDownUntil;
    }

    public Long getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(Long executedAt) {
        this.executedAt = executedAt;
    }

    public String getExecuteError() {
        return executeError;
    }

    public void setExecuteError(String executeError) {
        this.executeError = executeError;
    }
}
