package cn.slainc.modules.upms.dto;

import java.util.List;

public record RoleDataScopeView(String scopeType, List<Long> deptIds) {
}
