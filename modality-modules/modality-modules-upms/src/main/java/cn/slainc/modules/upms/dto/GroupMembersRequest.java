package cn.slainc.modules.upms.dto;

import java.util.List;

public record GroupMembersRequest(List<Long> userIds) {
}
