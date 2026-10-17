package cn.slainc.modules.open.excel;

import cn.idev.excel.annotation.ExcelProperty;
import cn.slainc.openapi.domain.App;
import cn.slainc.utils.date.DateUtils;

import java.util.Collection;

public class AppExportRow {

    @ExcelProperty("应用ID")
    private String appId;

    @ExcelProperty("应用名称")
    private String name;

    @ExcelProperty("应用类型")
    private String appType;

    @ExcelProperty("状态")
    private String status;

    @ExcelProperty("分类")
    private String category;

    @ExcelProperty("负责人ID")
    private String ownerId;

    @ExcelProperty("联系人")
    private String contact;

    @ExcelProperty("描述")
    private String description;

    @ExcelProperty("授权范围")
    private String scopes;

    @ExcelProperty("回调地址")
    private String redirectUris;

    @ExcelProperty("标签")
    private String tags;

    @ExcelProperty("创建时间")
    private String createdAt;

    @ExcelProperty("更新时间")
    private String updatedAt;

    public AppExportRow() {
    }

    public AppExportRow(App app) {
        this.appId = app.getAppId();
        this.name = app.getName();
        this.appType = app.getAppType().label();
        this.status = app.getStatus().label();
        this.category = app.getCategory();
        this.ownerId = app.getOwnerId();
        this.contact = app.getContact();
        this.description = app.getDescription();
        this.scopes = join(app.getScopes());
        this.redirectUris = join(app.getRedirectUris());
        this.tags = join(app.getTags());
        this.createdAt = DateUtils.format(DateUtils.fromEpochMilli(app.getCreatedAt()));
        this.updatedAt = DateUtils.format(DateUtils.fromEpochMilli(app.getUpdatedAt()));
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

    public String getAppType() {
        return appType;
    }

    public void setAppType(String appType) {
        this.appType = appType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getContact() {
        return contact;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getScopes() {
        return scopes;
    }

    public void setScopes(String scopes) {
        this.scopes = scopes;
    }

    public String getRedirectUris() {
        return redirectUris;
    }

    public void setRedirectUris(String redirectUris) {
        this.redirectUris = redirectUris;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    private static String join(Collection<String> values) {
        return values == null ? "" : String.join(", ", values);
    }
}
