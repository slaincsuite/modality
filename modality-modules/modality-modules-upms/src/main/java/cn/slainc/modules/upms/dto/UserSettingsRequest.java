package cn.slainc.modules.upms.dto;

public record UserSettingsRequest(Integer passwordMinLength, Integer passwordRequireClasses,
                                  Integer passwordExpireDays, Boolean passwordAllowSelfReset,
                                  String defaultLoginType, Long defaultRoleId, Boolean sendInitialPasswordEmail,
                                  Boolean forceChangePassword, Integer loginFailureThreshold,
                                  Integer loginLockMinutes, Integer idleTimeoutMinutes, Integer maxSessionMinutes,
                                  Boolean restrictIdleUsers, Integer idleUserDays) {
}
