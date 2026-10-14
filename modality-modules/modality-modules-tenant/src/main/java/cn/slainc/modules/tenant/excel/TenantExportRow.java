package cn.slainc.modules.tenant.excel;

import cn.idev.excel.annotation.ExcelProperty;
import cn.slainc.tenant.entity.Tenant;
import cn.slainc.utils.date.DateUtils;
import cn.slainc.utils.string.StringUtils;

public class TenantExportRow {

    @ExcelProperty("租户ID")
    private String id;

    @ExcelProperty("租户编码")
    private String tenantCode;

    @ExcelProperty("租户名称")
    private String tenantName;

    @ExcelProperty("状态")
    private String status;

    @ExcelProperty("隔离策略")
    private String isolationStrategy;

    @ExcelProperty("过期时间")
    private String expireAt;

    @ExcelProperty("联系人")
    private String contactName;

    @ExcelProperty("联系电话")
    private String contactPhone;

    @ExcelProperty("联系邮箱")
    private String contactEmail;

    public TenantExportRow() {
    }

    public TenantExportRow(Tenant tenant) {
        this.id = String.valueOf(tenant.getId());
        this.tenantCode = tenant.getTenantCode();
        this.tenantName = tenant.getTenantName();
        this.status = tenant.getStatus() == null ? "" : tenant.getStatus().name();
        this.isolationStrategy = tenant.getIsolationStrategy() == null ? "" : tenant.getIsolationStrategy().name();
        this.expireAt = tenant.getExpireAt() == null ? "" : DateUtils.format(tenant.getExpireAt());
        this.contactName = tenant.getContactName() == null ? "" : tenant.getContactName();
        this.contactPhone = StringUtils.maskMobile(tenant.getContactPhone());
        this.contactEmail = StringUtils.maskEmail(tenant.getContactEmail());
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTenantCode() {
        return tenantCode;
    }

    public void setTenantCode(String tenantCode) {
        this.tenantCode = tenantCode;
    }

    public String getTenantName() {
        return tenantName;
    }

    public void setTenantName(String tenantName) {
        this.tenantName = tenantName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getIsolationStrategy() {
        return isolationStrategy;
    }

    public void setIsolationStrategy(String isolationStrategy) {
        this.isolationStrategy = isolationStrategy;
    }

    public String getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(String expireAt) {
        this.expireAt = expireAt;
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }
}
