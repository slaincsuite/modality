package cn.slainc.modules.upms.dto;

public record UserCreateRequest(String username, String nickname, String password, String email, String phone,
                                Long deptId, String loginType, Long tenantId) {
}
