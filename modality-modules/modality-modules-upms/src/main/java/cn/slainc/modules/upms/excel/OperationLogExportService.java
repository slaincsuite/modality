package cn.slainc.modules.upms.excel;

import cn.idev.excel.annotation.ExcelProperty;
import cn.slainc.excel.ExcelUtils;
import cn.slainc.modules.upms.entity.UserOperationLogRecord;
import cn.slainc.modules.upms.log.OperationLogService;
import cn.slainc.utils.date.DateUtils;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class OperationLogExportService {

    private final OperationLogService operationLogService;

    public OperationLogExportService(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    public void export(OutputStream outputStream, int limit) {
        List<UserOperationLogRecord> records = operationLogService.list(null, null, null, null, null, null, null,
                Math.max(1, limit));
        List<OperationLogExportRow> rows = new ArrayList<>();
        for (UserOperationLogRecord record : records) {
            rows.add(new OperationLogExportRow(record.getUsername(), record.getModuleName(), record.getOperation(),
                    record.getDescription(), record.getSuccess() ? "成功" : "失败", record.getClientIp(),
                    record.getCreateTime() == null ? ""
                            : DateUtils.format(DateUtils.fromEpochMilli(record.getCreateTime())),
                    record.getCostMillis()));
        }
        ExcelUtils.write(outputStream, OperationLogExportRow.class, rows, "操作日志");
    }

    public static class OperationLogExportRow {

        @ExcelProperty("操作人")
        private String username;

        @ExcelProperty("模块")
        private String module;

        @ExcelProperty("操作")
        private String operation;

        @ExcelProperty("描述")
        private String description;

        @ExcelProperty("结果")
        private String result;

        @ExcelProperty("来源IP")
        private String clientIp;

        @ExcelProperty("时间")
        private String createTime;

        @ExcelProperty("耗时(毫秒)")
        private Long costMillis;

        public OperationLogExportRow() {
        }

        public OperationLogExportRow(String username, String module, String operation, String description,
                                     String result, String clientIp, String createTime, Long costMillis) {
            this.username = username;
            this.module = module;
            this.operation = operation;
            this.description = description;
            this.result = result;
            this.clientIp = clientIp;
            this.createTime = createTime;
            this.costMillis = costMillis;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getModule() {
            return module;
        }

        public void setModule(String module) {
            this.module = module;
        }

        public String getOperation() {
            return operation;
        }

        public void setOperation(String operation) {
            this.operation = operation;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getResult() {
            return result;
        }

        public void setResult(String result) {
            this.result = result;
        }

        public String getClientIp() {
            return clientIp;
        }

        public void setClientIp(String clientIp) {
            this.clientIp = clientIp;
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
    }
}
