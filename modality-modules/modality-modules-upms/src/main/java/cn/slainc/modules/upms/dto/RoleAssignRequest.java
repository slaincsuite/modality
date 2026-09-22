package cn.slainc.modules.upms.dto;

import java.util.List;

public record RoleAssignRequest(List<Long> roleIds) {
}
