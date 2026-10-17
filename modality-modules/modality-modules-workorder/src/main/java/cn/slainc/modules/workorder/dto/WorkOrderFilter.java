package cn.slainc.modules.workorder.dto;


public record WorkOrderFilter(String status, String priority, String typeCode, String keyword,
                              String from, String to) {

    public static WorkOrderFilter empty() {
        return new WorkOrderFilter(null, null, null, null, null, null);
    }
}
