package cn.slainc.modules.upms.dto;

public record UserUpdateRequest(String nickname, String email, String phone, Long deptId, String avatarUrl,
                                String tags, String extJson, String allowedIps, String allowedTimeRange,
                                String allowedDevices, String remark) {
}
