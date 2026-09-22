package cn.slainc.modules.upms.dto;

public record ChangePasswordRequest(String oldPassword, String newPassword) {
}
