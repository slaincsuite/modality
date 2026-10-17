package cn.slainc.modules.upms.entity;

import cn.slainc.modules.upms.UpmsKeys;
import cn.slainc.sensitive.annotation.Sensitive;
import cn.slainc.sensitive.annotation.SensitiveType;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

@TableName(UserRecord.TABLE_NAME)
public class UserRecord {

    public static final String TABLE_NAME = UpmsKeys.USER_TABLE;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private String username;

    private String nickname;

    // 口令哈希永不进 JSON；登录校验等内部路径仍走 getPasswordHash()
    @JsonIgnore
    private String passwordHash;

    @Sensitive(type = SensitiveType.EMAIL)
    private String email;

    @Sensitive(type = SensitiveType.PHONE)
    private String phone;

    private String avatarUrl;

    private Long deptId;

    private String loginType;

    private String status = UpmsKeys.STATUS_ENABLED;

    private Boolean mustChangePassword = false;

    private Long passwordChangedAt;

    @Sensitive(type = SensitiveType.CUSTOM)
    private String allowedIps;

    private String allowedTimeRange;

    private String allowedDevices;

    private String tags;

    private String extJson;

    private Long mergedTo;

    private String remark;

    private Boolean consoleAccess = true;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @TableField(exist = false)
    private String initialPassword;

    private Boolean deleted = false;

    private Long createTime;

    private Long updateTime;

    public boolean enabled() {
        return UpmsKeys.STATUS_ENABLED.equals(status) && !Boolean.FALSE.equals(consoleAccess);
    }

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

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }

    public String getLoginType() {
        return loginType;
    }

    public void setLoginType(String loginType) {
        this.loginType = loginType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getMustChangePassword() {
        return mustChangePassword;
    }

    public void setMustChangePassword(Boolean mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
    }

    public Long getPasswordChangedAt() {
        return passwordChangedAt;
    }

    public void setPasswordChangedAt(Long passwordChangedAt) {
        this.passwordChangedAt = passwordChangedAt;
    }

    public String getAllowedIps() {
        return allowedIps;
    }

    public void setAllowedIps(String allowedIps) {
        this.allowedIps = allowedIps;
    }

    public String getAllowedTimeRange() {
        return allowedTimeRange;
    }

    public void setAllowedTimeRange(String allowedTimeRange) {
        this.allowedTimeRange = allowedTimeRange;
    }

    public String getAllowedDevices() {
        return allowedDevices;
    }

    public void setAllowedDevices(String allowedDevices) {
        this.allowedDevices = allowedDevices;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getExtJson() {
        return extJson;
    }

    public void setExtJson(String extJson) {
        this.extJson = extJson;
    }

    public Long getMergedTo() {
        return mergedTo;
    }

    public void setMergedTo(Long mergedTo) {
        this.mergedTo = mergedTo;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Boolean getConsoleAccess() {
        return consoleAccess;
    }

    public void setConsoleAccess(Boolean consoleAccess) {
        this.consoleAccess = consoleAccess;
    }

    public String getInitialPassword() {
        return initialPassword;
    }

    public void setInitialPassword(String initialPassword) {
        this.initialPassword = initialPassword;
    }

    public Boolean getDeleted() {
        return deleted;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
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
