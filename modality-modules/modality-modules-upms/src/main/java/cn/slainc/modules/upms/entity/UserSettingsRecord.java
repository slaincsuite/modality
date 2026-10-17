package cn.slainc.modules.upms.entity;

import cn.slainc.modules.upms.UpmsKeys;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName(UserSettingsRecord.TABLE_NAME)
public class UserSettingsRecord {

    public static final String TABLE_NAME = UpmsKeys.USER_SETTINGS_TABLE;

    public static final Long SINGLETON_ID = 1L;

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    private Integer passwordMinLength;

    private Integer passwordRequireClasses;

    private Integer passwordExpireDays;

    private Boolean passwordAllowSelfReset;

    private String defaultLoginType;

    private Long defaultRoleId;

    private Boolean sendInitialPasswordEmail;

    private Boolean forceChangePassword;

    private Integer loginFailureThreshold;

    private Integer loginLockMinutes;

    private Integer idleTimeoutMinutes;

    private Integer maxSessionMinutes;

    private Boolean restrictIdleUsers;

    private Integer idleUserDays;

    private Long createTime;

    private Long updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getPasswordMinLength() {
        return passwordMinLength;
    }

    public void setPasswordMinLength(Integer passwordMinLength) {
        this.passwordMinLength = passwordMinLength;
    }

    public Integer getPasswordRequireClasses() {
        return passwordRequireClasses;
    }

    public void setPasswordRequireClasses(Integer passwordRequireClasses) {
        this.passwordRequireClasses = passwordRequireClasses;
    }

    public Integer getPasswordExpireDays() {
        return passwordExpireDays;
    }

    public void setPasswordExpireDays(Integer passwordExpireDays) {
        this.passwordExpireDays = passwordExpireDays;
    }

    public Boolean getPasswordAllowSelfReset() {
        return passwordAllowSelfReset;
    }

    public void setPasswordAllowSelfReset(Boolean passwordAllowSelfReset) {
        this.passwordAllowSelfReset = passwordAllowSelfReset;
    }

    public String getDefaultLoginType() {
        return defaultLoginType;
    }

    public void setDefaultLoginType(String defaultLoginType) {
        this.defaultLoginType = defaultLoginType;
    }

    public Long getDefaultRoleId() {
        return defaultRoleId;
    }

    public void setDefaultRoleId(Long defaultRoleId) {
        this.defaultRoleId = defaultRoleId;
    }

    public Boolean getSendInitialPasswordEmail() {
        return sendInitialPasswordEmail;
    }

    public void setSendInitialPasswordEmail(Boolean sendInitialPasswordEmail) {
        this.sendInitialPasswordEmail = sendInitialPasswordEmail;
    }

    public Boolean getForceChangePassword() {
        return forceChangePassword;
    }

    public void setForceChangePassword(Boolean forceChangePassword) {
        this.forceChangePassword = forceChangePassword;
    }

    public Integer getLoginFailureThreshold() {
        return loginFailureThreshold;
    }

    public void setLoginFailureThreshold(Integer loginFailureThreshold) {
        this.loginFailureThreshold = loginFailureThreshold;
    }

    public Integer getLoginLockMinutes() {
        return loginLockMinutes;
    }

    public void setLoginLockMinutes(Integer loginLockMinutes) {
        this.loginLockMinutes = loginLockMinutes;
    }

    public Integer getIdleTimeoutMinutes() {
        return idleTimeoutMinutes;
    }

    public void setIdleTimeoutMinutes(Integer idleTimeoutMinutes) {
        this.idleTimeoutMinutes = idleTimeoutMinutes;
    }

    public Integer getMaxSessionMinutes() {
        return maxSessionMinutes;
    }

    public void setMaxSessionMinutes(Integer maxSessionMinutes) {
        this.maxSessionMinutes = maxSessionMinutes;
    }

    public Boolean getRestrictIdleUsers() {
        return restrictIdleUsers;
    }

    public void setRestrictIdleUsers(Boolean restrictIdleUsers) {
        this.restrictIdleUsers = restrictIdleUsers;
    }

    public Integer getIdleUserDays() {
        return idleUserDays;
    }

    public void setIdleUserDays(Integer idleUserDays) {
        this.idleUserDays = idleUserDays;
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
