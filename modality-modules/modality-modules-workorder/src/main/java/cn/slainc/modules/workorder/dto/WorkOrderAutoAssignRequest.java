package cn.slainc.modules.workorder.dto;

import java.util.List;


public record WorkOrderAutoAssignRequest(List<Long> candidates, String strategy) {
}
