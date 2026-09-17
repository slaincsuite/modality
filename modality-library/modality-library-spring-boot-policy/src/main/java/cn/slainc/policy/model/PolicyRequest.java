package cn.slainc.policy.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PolicyRequest {

    public static final String KEY_USER_ID = "modality:UserId";

    public static final String KEY_TENANT_ID = "modality:TenantId";

    public static final String KEY_SOURCE_IP = "modality:SourceIp";

    public static final String KEY_CURRENT_TIME = "modality:CurrentTime";

    private String userId;

    private String loginType;

    private Long tenantId;

    private List<String> roles = new ArrayList<>();

    private String action;

    private String resource;

    private Map<String, Object> env = new LinkedHashMap<>();

    public static PolicyRequest of(String action, String resource) {
        PolicyRequest request = new PolicyRequest();
        request.setAction(action);
        request.setResource(resource);
        return request;
    }

    public PolicyRequest userId(String userId) {
        this.userId = userId;
        return this;
    }

    public PolicyRequest loginType(String loginType) {
        this.loginType = loginType;
        return this;
    }

    public PolicyRequest tenantId(Long tenantId) {
        this.tenantId = tenantId;
        return this;
    }

    public PolicyRequest roles(List<String> roles) {
        this.roles = roles == null ? new ArrayList<>() : roles;
        return this;
    }

    public PolicyRequest env(String key, Object value) {
        this.env.put(key, value);
        return this;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
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

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
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

    public Map<String, Object> getEnv() {
        return env;
    }

    public void setEnv(Map<String, Object> env) {
        this.env = env;
    }
}
