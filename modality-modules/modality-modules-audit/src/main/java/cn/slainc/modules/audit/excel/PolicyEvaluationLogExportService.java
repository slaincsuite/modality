package cn.slainc.modules.audit.excel;

import cn.idev.excel.annotation.ExcelProperty;
import cn.slainc.excel.ExcelUtils;
import cn.slainc.modules.audit.entity.PolicyEvaluationLogRecord;
import cn.slainc.modules.audit.service.policylog.PolicyEvaluationLogService;
import cn.slainc.utils.date.DateUtils;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class PolicyEvaluationLogExportService {

    private final PolicyEvaluationLogService logService;

    public PolicyEvaluationLogExportService(PolicyEvaluationLogService logService) {
        this.logService = logService;
    }

    public void export(OutputStream outputStream, String loginId, String loginType, String action, String resource,
                       String keyword, String decision, Long from, Long to, int limit) {
        List<PolicyEvaluationLogRecord> records = logService.list(loginId, loginType, action, resource, keyword,
                decision, from, to, Math.max(1, limit));
        List<PolicyEvaluationLogExportRow> rows = new ArrayList<>(records.size());
        for (PolicyEvaluationLogRecord record : records) {
            rows.add(new PolicyEvaluationLogExportRow(record.getLoginId(), record.getLoginType(), record.getAction(),
                    record.getResource(), record.getDecision(), record.getReason(), record.getClientIp(),
                    record.getRequestPath(),
                    record.getCreateTime() == null ? null
                            : DateUtils.format(DateUtils.fromEpochMilli(record.getCreateTime()))));
        }
        ExcelUtils.write(outputStream, PolicyEvaluationLogExportRow.class, rows, "策略评估日志");
    }

    public static class PolicyEvaluationLogExportRow {

        @ExcelProperty("登录账号")
        private String loginId;

        @ExcelProperty("账号体系")
        private String loginType;

        @ExcelProperty("动作")
        private String action;

        @ExcelProperty("资源")
        private String resource;

        @ExcelProperty("决策")
        private String decision;

        @ExcelProperty("原因")
        private String reason;

        @ExcelProperty("来源IP")
        private String clientIp;

        @ExcelProperty("请求路径")
        private String requestPath;

        @ExcelProperty("时间")
        private String createTime;

        public PolicyEvaluationLogExportRow() {
        }

        public PolicyEvaluationLogExportRow(String loginId, String loginType, String action, String resource,
                                            String decision, String reason, String clientIp, String requestPath,
                                            String createTime) {
            this.loginId = loginId;
            this.loginType = loginType;
            this.action = action;
            this.resource = resource;
            this.decision = decision;
            this.reason = reason;
            this.clientIp = clientIp;
            this.requestPath = requestPath;
            this.createTime = createTime;
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

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public String getResource() {
            return resource;
        }

        public void setResource(String resource) {
            this.resource = resource;
        }

        public String getDecision() {
            return decision;
        }

        public void setDecision(String decision) {
            this.decision = decision;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
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
    }
}
