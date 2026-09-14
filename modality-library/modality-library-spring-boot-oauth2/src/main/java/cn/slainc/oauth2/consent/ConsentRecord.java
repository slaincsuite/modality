package cn.slainc.oauth2.consent;

import java.util.ArrayList;
import java.util.List;

public class ConsentRecord {

    private Long id;

    private String loginId;

    private String clientId;

    private List<String> scopes = new ArrayList<>();

    private long grantedAt;

    private Long revokedAt;

    public boolean active() {
        return revokedAt == null;
    }

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

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public List<String> getScopes() {
        return scopes;
    }

    public void setScopes(List<String> scopes) {
        this.scopes = scopes;
    }

    public long getGrantedAt() {
        return grantedAt;
    }

    public void setGrantedAt(long grantedAt) {
        this.grantedAt = grantedAt;
    }

    public Long getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Long revokedAt) {
        this.revokedAt = revokedAt;
    }
}
