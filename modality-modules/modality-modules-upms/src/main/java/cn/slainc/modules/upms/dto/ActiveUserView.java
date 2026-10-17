package cn.slainc.modules.upms.dto;

public record ActiveUserView(Long userId, String username, long operationCount, Long lastActiveTime) {
}
