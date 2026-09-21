package cn.slainc.modules.upms.entity;

import cn.slainc.modules.upms.UpmsKeys;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName(AccessControlRecord.TABLE_NAME)
public class AccessControlRecord {

    public static final String TABLE_NAME = UpmsKeys.ACCESS_CONTROL_TABLE;

    public static final String STATUS_GRANTED = "granted";

    public static final String STATUS_REVOKED = "revoked";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long systemId;

    private Long userId;

    private String grantedBy;

    private Long grantedAt;

    private String status = STATUS_GRANTED;

    public boolean granted() {
        return STATUS_GRANTED.equals(status);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getSystemId() {
        return systemId;
    }

    public void setSystemId(Long systemId) {
        this.systemId = systemId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getGrantedBy() {
        return grantedBy;
    }

    public void setGrantedBy(String grantedBy) {
        this.grantedBy = grantedBy;
    }

    public Long getGrantedAt() {
        return grantedAt;
    }

    public void setGrantedAt(Long grantedAt) {
        this.grantedAt = grantedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
