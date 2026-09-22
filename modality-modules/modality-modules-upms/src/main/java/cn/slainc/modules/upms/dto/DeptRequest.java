package cn.slainc.modules.upms.dto;

public record DeptRequest(String name, Long parentId, Long leaderUserId, Integer sort, String status) {
}
