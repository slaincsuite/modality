package cn.slainc.modules.audit.excel;

import cn.idev.excel.annotation.ExcelProperty;
import cn.slainc.excel.ExcelUtils;
import cn.slainc.modules.audit.service.AuditLogService;
import cn.slainc.modules.audit.entity.AuditLogRecord;
import cn.slainc.utils.date.DateUtils;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;


public class AuditLogExportService {

    private final AuditLogService auditLogService;

    public AuditLogExportService(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    public void export(OutputStream outputStream, String category, String operation, String level, String module,
                       String username, String loginType, Boolean success, Long from, Long to, int limit) {
        List<AuditLogRecord> records = auditLogService.list(category, operation, level, module, username, loginType,
                success, from, to, Math.max(1, limit));
        List<AuditLogExportRow> rows = new ArrayList<>();
        for (AuditLogRecord record : records) {
            rows.add(new AuditLogExportRow(record.getCategory(), record.getOperation(), record.getLevel(),
                    record.getModule(), record.getDescription(),
                    Boolean.TRUE.equals(record.getSuccess()) ? "成功" : "失败",
                    record.getUsername() == null ? record.getLoginId() : record.getUsername(),
                    record.getLoginType(), record.getClientIp(), record.getRequestPath(),
                    record.getCreateTime() == null ? null
                            : DateUtils.format(DateUtils.fromEpochMilli(record.getCreateTime())),
                    record.getCostMillis(), record.getTraceId()));
        }
        ExcelUtils.write(outputStream, AuditLogExportRow.class, rows, "审计日志");
    }

    public static class AuditLogExportRow {

        @ExcelProperty("类别")
        private String category;

        @ExcelProperty("操作")
        private String operation;

        @ExcelProperty("级别")
        private String level;

        @ExcelProperty("模块")
        private String module;

        @ExcelProperty("描述")
        private String description;

        @ExcelProperty("结果")
        private String success;

        @ExcelProperty("操作人")
        private String username;

        @ExcelProperty("账号体系")
        private String loginType;

        @ExcelProperty("来源IP")
        private String clientIp;

        @ExcelProperty("请求路径")
        private String requestPath;

        @ExcelProperty("时间")
        private String createTime;

        @ExcelProperty("耗时(毫秒)")
        private Long costMillis;

        @ExcelProperty("追踪ID")
        private String traceId;

        public AuditLogExportRow() {
        }

        public AuditLogExportRow(String category, String operation, String level, String module, String description,
                                 String success, String username, String loginType, String clientIp,
                                 String requestPath, String createTime, Long costMillis, String traceId) {
            this.category = category;
            this.operation = operation;
            this.level = level;
            this.module = module;
            this.description = description;
            this.success = success;
            this.username = username;
            this.loginType = loginType;
            this.clientIp = clientIp;
            this.requestPath = requestPath;
            this.createTime = createTime;
            this.costMillis = costMillis;
            this.traceId = traceId;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getOperation() {
            return operation;
        }

        public void setOperation(String operation) {
            this.operation = operation;
        }

        public String getLevel() {
            return level;
        }

        public void setLevel(String level) {
            this.level = level;
        }

        public String getModule() {
            return module;
        }

        public void setModule(String module) {
            this.module = module;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getSuccess() {
            return success;
        }

        public void setSuccess(String success) {
            this.success = success;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getLoginType() {
            return loginType;
        }

        public void setLoginType(String loginType) {
            this.loginType = loginType;
        }

        public String getClientIp() {
            return clientIp;
        }

        public void setClientIp(String clientIp) {
            this.clientIp = clientIp;
        }

        public String getRequestPath() {
            return requestPath;
        }

        public void setRequestPath(String requestPath) {
            this.requestPath = requestPath;
        }

        public String getCreateTime() {
            return createTime;
        }

        public void setCreateTime(String createTime) {
            this.createTime = createTime;
        }

        public Long getCostMillis() {
            return costMillis;
        }

        public void setCostMillis(Long costMillis) {
            this.costMillis = costMillis;
        }

        public String getTraceId() {
            return traceId;
        }

        public void setTraceId(String traceId) {
            this.traceId = traceId;
        }
    }
}
