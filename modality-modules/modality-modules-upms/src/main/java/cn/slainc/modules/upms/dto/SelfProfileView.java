package cn.slainc.modules.upms.dto;

import cn.slainc.modules.upms.entity.UserRecord;

public record SelfProfileView(Long id, Long tenantId, String username, String nickname, String email,
                              String phone, String avatarUrl, Long deptId, String loginType, String status,
                              Boolean mustChangePassword, Long passwordChangedAt, String remark,
                              Boolean consoleAccess, Long createTime, Long updateTime) {

    public static SelfProfileView of(UserRecord user) {
        return new SelfProfileView(user.getId(), user.getTenantId(), user.getUsername(), user.getNickname(),
                user.getEmail(), user.getPhone(), user.getAvatarUrl(), user.getDeptId(), user.getLoginType(),
                user.getStatus(), user.getMustChangePassword(), user.getPasswordChangedAt(), user.getRemark(),
                user.getConsoleAccess(), user.getCreateTime(), user.getUpdateTime());
    }
}
