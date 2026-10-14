package cn.slainc.openapi.domain;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class App {

    private static final Map<AppStatus, Map<AppAction, AppStatus>> TRANSITIONS = Map.of(
            AppStatus.DRAFT, Map.of(AppAction.SUBMIT, AppStatus.REVIEWING),
            AppStatus.REVIEWING, Map.of(AppAction.APPROVE, AppStatus.ONLINE, AppAction.REJECT, AppStatus.DRAFT),
            AppStatus.ONLINE, Map.of(AppAction.OFFLINE, AppStatus.OFFLINE),
            AppStatus.OFFLINE, Map.of(AppAction.REPUBLISH, AppStatus.REVIEWING));

    private static final SecureRandom RANDOM = new SecureRandom();

    private String appId;

    private String name;

    private String description = "";

    private String logo;

    private String category;

    private String contact;

    private List<String> tags = new ArrayList<>();

    private AppType appType;

    private AppStatus status = AppStatus.DRAFT;

    private String ownerId;

    private List<String> redirectUris = new ArrayList<>();

    private List<String> scopes = new ArrayList<>();

    private long createdAt;

    private long updatedAt;

    public static App create(String name, AppType appType, String ownerId) {
        if (name == null || name.isBlank()) {
            throw new BizException(ResultCode.PARAM_MISSING, "应用名称不能为空");
        }
        if (appType == null) {
            throw new BizException(ResultCode.PARAM_MISSING, "应用类型不能为空");
        }
        if (ownerId == null || ownerId.isBlank()) {
            throw new BizException(ResultCode.PARAM_MISSING, "应用负责人不能为空");
        }
        App app = new App();
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        app.setAppId("app_" + java.util.HexFormat.of().formatHex(bytes));
        app.setName(name.trim());
        app.setAppType(appType);
        app.setOwnerId(ownerId.trim());
        long now = System.currentTimeMillis();
        app.setCreatedAt(now);
        app.setUpdatedAt(now);
        return app;
    }

    public void transition(AppAction action) {
        Map<AppAction, AppStatus> allowed = TRANSITIONS.get(status);
        AppStatus next = allowed == null ? null : allowed.get(action);
        if (next == null) {
            throw new BizException(ResultCode.DATA_STATE_INVALID,
                    "应用当前状态「" + status.label() + "」不允许" + action.label());
        }
        this.status = next;
        this.updatedAt = System.currentTimeMillis();
    }

    public boolean editable() {
        return status == AppStatus.DRAFT || status == AppStatus.OFFLINE;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLogo() {
        return logo;
    }

    public void setLogo(String logo) {
        this.logo = logo;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getContact() {
        return contact;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public AppType getAppType() {
        return appType;
    }

    public void setAppType(AppType appType) {
        this.appType = appType;
    }

    public void setAppType(String appType) {
        this.appType = AppType.valueOf(appType.trim().toUpperCase());
    }

    public AppStatus getStatus() {
        return status;
    }

    public void setStatus(AppStatus status) {
        this.status = status;
    }

    public void setStatus(String status) {
        this.status = AppStatus.valueOf(status.trim().toUpperCase());
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public List<String> getRedirectUris() {
        return redirectUris;
    }

    public void setRedirectUris(List<String> redirectUris) {
        this.redirectUris = redirectUris;
    }

    public List<String> getScopes() {
        return scopes;
    }

    public void setScopes(List<String> scopes) {
        this.scopes = scopes;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
