package cn.slainc.modules.upms.dto;

import java.util.List;

public record RoleUsersRequest(List<Long> userIds) {
}
