package cn.slainc.modules.auth.policy;

public class SecurityPolicy {

    public static final long DEFAULT_ID = 1L;

    private Long id;

    private Boolean loginProtect;

    private Boolean operationProtect;

    private Boolean remoteCheck;

    private Long idleTimeoutSeconds;

    private Long maxSessionSeconds;

    private Long createTime;

    private Long updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Boolean getLoginProtect() {
        return loginProtect;
    }

    public void setLoginProtect(Boolean loginProtect) {
        this.loginProtect = loginProtect;
    }

    public Boolean getOperationProtect() {
        return operationProtect;
    }

    public void setOperationProtect(Boolean operationProtect) {
        this.operationProtect = operationProtect;
    }

    public Boolean getRemoteCheck() {
        return remoteCheck;
    }

    public void setRemoteCheck(Boolean remoteCheck) {
        this.remoteCheck = remoteCheck;
    }

    public Long getIdleTimeoutSeconds() {
        return idleTimeoutSeconds;
    }

    public void setIdleTimeoutSeconds(Long idleTimeoutSeconds) {
        this.idleTimeoutSeconds = idleTimeoutSeconds;
    }

    public Long getMaxSessionSeconds() {
        return maxSessionSeconds;
    }

    public void setMaxSessionSeconds(Long maxSessionSeconds) {
        this.maxSessionSeconds = maxSessionSeconds;
    }

    public Long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Long createTime) {
        this.createTime = createTime;
    }

    public Long getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Long updateTime) {
        this.updateTime = updateTime;
    }
}
