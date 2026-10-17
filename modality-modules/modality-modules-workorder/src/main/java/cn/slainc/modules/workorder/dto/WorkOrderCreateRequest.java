package cn.slainc.modules.workorder.dto;

import java.util.Map;


public record WorkOrderCreateRequest(String title, String content, String priority, String typeCode,
                                     Map<String, Object> customFields) {
}
