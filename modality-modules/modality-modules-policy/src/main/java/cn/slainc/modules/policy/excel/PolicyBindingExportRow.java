package cn.slainc.modules.policy.excel;

import cn.idev.excel.annotation.ExcelProperty;
import cn.slainc.modules.policy.dto.PolicyBindingView;
import cn.slainc.utils.date.DateUtils;

public class PolicyBindingExportRow {

    @ExcelProperty("源策略标识")
    private String sourcePolicyId;

    @ExcelProperty("绑定策略标识")
    private String policyId;

    @ExcelProperty("主体类型")
    private String subjectType;

    @ExcelProperty("主体标识")
    private String subjectId;

    @ExcelProperty("租户标识")
    private String tenantId;

    @ExcelProperty("操作人")
    private String operator;

    @ExcelProperty("绑定时间")
    private String createTime;

    public PolicyBindingExportRow() {
    }

    public PolicyBindingExportRow(String sourcePolicyId, PolicyBindingView view) {
        this.sourcePolicyId = sourcePolicyId;
        this.policyId = view.policyId();
        this.subjectType = view.subjectType();
        this.subjectId = view.subjectId();
        this.tenantId = view.tenantId() == null ? "" : String.valueOf(view.tenantId());
        this.operator = view.operator();
        this.createTime = view.createTime() == null || view.createTime() <= 0 ? ""
                : DateUtils.format(DateUtils.fromEpochMilli(view.createTime()));
    }

    public String getSourcePolicyId() {
        return sourcePolicyId;
    }

    public void setSourcePolicyId(String sourcePolicyId) {
        this.sourcePolicyId = sourcePolicyId;
    }

    public String getPolicyId() {
        return policyId;
    }

    public void setPolicyId(String policyId) {
        this.policyId = policyId;
    }

    public String getSubjectType() {
        return subjectType;
    }

    public void setSubjectType(String subjectType) {
        this.subjectType = subjectType;
    }

    public String getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(String subjectId) {
        this.subjectId = subjectId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }
}
