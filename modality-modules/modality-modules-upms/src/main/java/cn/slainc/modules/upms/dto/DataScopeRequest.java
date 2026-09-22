package cn.slainc.modules.upms.dto;

import java.util.List;

public record DataScopeRequest(String dataScope, List<Long> deptIds) {
}
