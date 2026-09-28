package cn.slainc.modules.audit.entity;

import cn.slainc.modules.audit.AuditKeys;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;


@TableName(PolicyEvaluationLogRecord.TABLE_NAME)
public class PolicyEvaluationLogRecord {

    public static final String TABLE_NAME = AuditKeys.POLICY_LOG_TABLE;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String loginId;

    private String loginType;

    private Long tenantId;

    private String action;

    private String resource;

    private String decision;

    private String matchedStatements;

    private String reason;

    private String clientIp;

    private String requestPath;

    private Long createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
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

    public String getMatchedStatements() {
        return matchedStatements;
    }

    public void setMatchedStatements(String matchedStatements) {
        this.matchedStatements = matchedStatements;
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

    public Long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Long createTime) {
        this.createTime = createTime;
    }
}
