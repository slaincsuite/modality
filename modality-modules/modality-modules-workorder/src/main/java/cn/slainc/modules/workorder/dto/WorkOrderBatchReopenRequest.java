package cn.slainc.modules.workorder.dto;

import java.util.List;


public record WorkOrderBatchReopenRequest(List<Long> ids, String status, String typeCode,
                                          String from, String to) {
}
