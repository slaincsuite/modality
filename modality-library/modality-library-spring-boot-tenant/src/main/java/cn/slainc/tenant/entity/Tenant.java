package cn.slainc.tenant.entity;

import cn.slainc.mybatis.entity.MybatisBaseEntity;
import cn.slainc.tenant.enums.TenantStatus;
import cn.slainc.tenant.isolation.TenantIsolationStrategy;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("t_tenant")
public class Tenant extends MybatisBaseEntity {

    private String tenantCode;

    private String tenantName;

    private String contactName;

    private String contactPhone;

    private String contactEmail;

    private LocalDateTime expireAt;

    private TenantStatus status = TenantStatus.TRIAL;

    private TenantIsolationStrategy isolationStrategy = TenantIsolationStrategy.ROW;

    private String schemaName;

    private String datasourceName;

    private Long packageId;

    private String remark;

    public boolean isExpired(LocalDateTime moment) {
        return expireAt != null && moment != null && expireAt.isBefore(moment);
    }

    public boolean isUsable(LocalDateTime moment) {
        return status != null && status.isUsable() && !isExpired(moment);
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

    public LocalDateTime getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(LocalDateTime expireAt) {
        this.expireAt = expireAt;
    }

    public TenantStatus getStatus() {
        return status;
    }

    public void setStatus(TenantStatus status) {
        this.status = status;
    }

    public TenantIsolationStrategy getIsolationStrategy() {
        return isolationStrategy;
    }

    public void setIsolationStrategy(TenantIsolationStrategy isolationStrategy) {
        this.isolationStrategy = isolationStrategy;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public void setSchemaName(String schemaName) {
        this.schemaName = schemaName;
    }

    public String getDatasourceName() {
        return datasourceName;
    }

    public void setDatasourceName(String datasourceName) {
        this.datasourceName = datasourceName;
    }

    public Long getPackageId() {
        return packageId;
    }

    public void setPackageId(Long packageId) {
        this.packageId = packageId;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
