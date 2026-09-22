package cn.slainc.modules.upms.dto;

import java.util.List;

public record GrantRequest(Long systemId, List<Long> userIds) {
}
