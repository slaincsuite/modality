package cn.slainc.modules.workorder.excel;

import cn.idev.excel.annotation.ExcelProperty;
import cn.slainc.excel.ExcelUtils;
import cn.slainc.modules.workorder.entity.WorkOrderRecord;
import cn.slainc.modules.workorder.service.WorkOrderService;
import cn.slainc.utils.date.DateUtils;
import cn.slainc.web.http.DownloadResponses;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;


public class WorkOrderExportService {

    private final WorkOrderService workOrderService;

    public WorkOrderExportService(WorkOrderService workOrderService) {
        this.workOrderService = workOrderService;
    }

    public void export(String status, String priority, String keyword, HttpServletResponse response) {
        List<WorkOrderRecord> records = workOrderService.list(status, priority, keyword,
                WorkOrderService.MAX_EXPORT_LIMIT);
        List<WorkOrderExportRow> rows = new ArrayList<>();
        for (WorkOrderRecord record : records) {
            rows.add(new WorkOrderExportRow(record.getNumber(), record.getTitle(), record.getContent(),
                    record.getTypeCode(), record.getPriority(), record.getStatus(),
                    record.getSubmitterId() == null ? null : String.valueOf(record.getSubmitterId()),
                    record.getAssigneeId() == null ? null : String.valueOf(record.getAssigneeId()),
                    record.getCreateTime() == null ? null
                            : DateUtils.format(DateUtils.fromEpochMilli(record.getCreateTime()))));
        }
        ExcelUtils.write(DownloadResponses.attachmentStream(response, "work-orders.xlsx",
                DownloadResponses.CONTENT_TYPE_EXCEL), WorkOrderExportRow.class, rows, "工单");
    }

    public static class WorkOrderExportRow {

        @ExcelProperty("编号")
        private String number;

        @ExcelProperty("标题")
        private String title;

        @ExcelProperty("内容")
        private String content;

        @ExcelProperty("类型")
        private String typeCode;

        @ExcelProperty("优先级")
        private String priority;

        @ExcelProperty("状态")
        private String status;

        @ExcelProperty("提交人")
        private String submitterId;

        @ExcelProperty("处理人")
        private String assigneeId;

        @ExcelProperty("创建时间")
        private String createTime;

        public WorkOrderExportRow() {
        }

        public WorkOrderExportRow(String number, String title, String content, String typeCode, String priority,
                                  String status, String submitterId, String assigneeId, String createTime) {
            this.number = number;
            this.title = title;
            this.content = content;
            this.typeCode = typeCode;
            this.priority = priority;
            this.status = status;
            this.submitterId = submitterId;
            this.assigneeId = assigneeId;
            this.createTime = createTime;
        }

        public String getNumber() {
            return number;
        }

        public void setNumber(String number) {
            this.number = number;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getTypeCode() {
            return typeCode;
        }

        public void setTypeCode(String typeCode) {
            this.typeCode = typeCode;
        }

        public String getPriority() {
            return priority;
        }

        public void setPriority(String priority) {
            this.priority = priority;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getSubmitterId() {
            return submitterId;
        }

        public void setSubmitterId(String submitterId) {
            this.submitterId = submitterId;
        }

        public String getAssigneeId() {
            return assigneeId;
        }

        public void setAssigneeId(String assigneeId) {
            this.assigneeId = assigneeId;
        }

        public String getCreateTime() {
            return createTime;
        }

        public void setCreateTime(String createTime) {
            this.createTime = createTime;
        }
    }
}
