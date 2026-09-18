package cn.slainc.openapi.model;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class ConsentRecord {

    private static final SecureRandom RANDOM = new SecureRandom();

    public static final String SOURCE_PORTAL = "PORTAL";

    public static final String SOURCE_ADMIN = "ADMIN";

    private String id;

    private String userId;

    private String appId;

    private List<String> scopes = new ArrayList<>();

    private long grantedAt;

    private Long revokedAt;

    private String source = SOURCE_PORTAL;

    public static ConsentRecord create(String userId, String appId, List<String> scopes, String source) {
        ConsentRecord record = new ConsentRecord();
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        record.setId("cst_" + java.util.HexFormat.of().formatHex(bytes));
        record.setUserId(userId);
        record.setAppId(appId);
        record.setScopes(new ArrayList<>(scopes));
        record.setGrantedAt(System.currentTimeMillis());
        record.setSource(source == null || source.isBlank() ? SOURCE_PORTAL : source);
        return record;
    }

    public boolean active() {
        return revokedAt == null;
    }

    public ConsentRecord mergeScopes(List<String> incoming) {
        LinkedHashSet<String> merged = new LinkedHashSet<>(this.scopes);
        merged.addAll(incoming);
        this.scopes = new ArrayList<>(merged);
        return this;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
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

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
