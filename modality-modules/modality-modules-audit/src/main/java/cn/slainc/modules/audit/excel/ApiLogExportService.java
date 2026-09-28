package cn.slainc.modules.audit.excel;

import cn.idev.excel.annotation.ExcelProperty;
import cn.slainc.excel.ExcelUtils;
import cn.slainc.modules.audit.service.ApiLogService;
import cn.slainc.modules.audit.entity.ApiLogRecord;
import java.io.OutputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;


public class ApiLogExportService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ApiLogService apiLogService;

    public ApiLogExportService(ApiLogService apiLogService) {
        this.apiLogService = apiLogService;
    }

    public void export(OutputStream outputStream, String path, String method, Integer statusCode, String loginId,
                       String loginType, Long from, Long to, int limit) {
        List<ApiLogRecord> records = apiLogService.list(path, method, statusCode, loginId, loginType, from, to,
                Math.max(1, limit));
        List<ApiLogExportRow> rows = new ArrayList<>();
        for (ApiLogRecord record : records) {
            rows.add(new ApiLogExportRow(record.getRequestPath(), record.getHttpMethod(),
                    record.getStatusCode() == null ? null : String.valueOf(record.getStatusCode()),
                    Boolean.TRUE.equals(record.getSlow()) ? "是" : "否",
                    Boolean.TRUE.equals(record.getError()) ? "是" : "否",
                    record.getCostMillis(), record.getLoginId(), record.getLoginType(), record.getClientIp(),
                    formatTime(record.getCreateTime()), record.getTraceId()));
        }
        ExcelUtils.write(outputStream, ApiLogExportRow.class, rows, "API访问日志");
    }

    private String formatTime(Long epochMillis) {
        if (epochMillis == null) {
            return null;
        }
        return TIME_FORMATTER.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()));
    }

    public static class ApiLogExportRow {

        @ExcelProperty("请求路径")
        private String requestPath;

        @ExcelProperty("方法")
        private String httpMethod;

        @ExcelProperty("状态码")
        private String statusCode;

        @ExcelProperty("慢请求")
        private String slow;

        @ExcelProperty("错误请求")
        private String error;

        @ExcelProperty("耗时(毫秒)")
        private Long costMillis;

        @ExcelProperty("账号")
        private String loginId;

        @ExcelProperty("账号体系")
        private String loginType;

        @ExcelProperty("来源IP")
        private String clientIp;

        @ExcelProperty("时间")
        private String createTime;

        @ExcelProperty("追踪ID")
        private String traceId;

        public ApiLogExportRow() {
        }

        public ApiLogExportRow(String requestPath, String httpMethod, String statusCode, String slow, String error,
                               Long costMillis, String loginId, String loginType, String clientIp,
                               String createTime, String traceId) {
            this.requestPath = requestPath;
            this.httpMethod = httpMethod;
            this.statusCode = statusCode;
            this.slow = slow;
            this.error = error;
            this.costMillis = costMillis;
            this.loginId = loginId;
            this.loginType = loginType;
            this.clientIp = clientIp;
            this.createTime = createTime;
            this.traceId = traceId;
        }

        public String getRequestPath() {
            return requestPath;
        }

        public void setRequestPath(String requestPath) {
            this.requestPath = requestPath;
        }

        public String getHttpMethod() {
            return httpMethod;
        }

        public void setHttpMethod(String httpMethod) {
            this.httpMethod = httpMethod;
        }

        public String getStatusCode() {
            return statusCode;
        }

        public void setStatusCode(String statusCode) {
            this.statusCode = statusCode;
        }

        public String getSlow() {
            return slow;
        }

        public void setSlow(String slow) {
            this.slow = slow;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }

        public Long getCostMillis() {
            return costMillis;
        }

        public void setCostMillis(Long costMillis) {
            this.costMillis = costMillis;
        }

        public String getLoginId() {
            return loginId;
        }

        public void setLoginId(String loginId) {
            this.loginId = loginId;
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

        public String getCreateTime() {
            return createTime;
        }

        public void setCreateTime(String createTime) {
            this.createTime = createTime;
        }

        public String getTraceId() {
            return traceId;
        }

        public void setTraceId(String traceId) {
            this.traceId = traceId;
        }
    }
}
