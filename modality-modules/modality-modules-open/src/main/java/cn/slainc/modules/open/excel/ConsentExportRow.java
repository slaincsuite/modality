package cn.slainc.modules.open.excel;

import cn.idev.excel.annotation.ExcelProperty;
import cn.slainc.modules.open.dto.ConsentView;
import cn.slainc.utils.date.DateUtils;

import java.util.Optional;

public class ConsentExportRow {

    @ExcelProperty("记录ID")
    private String id;

    @ExcelProperty("用户ID")
    private String userId;

    @ExcelProperty("应用ID")
    private String appId;

    @ExcelProperty("应用名称")
    private String appName;

    @ExcelProperty("授权Scope")
    private String scopes;

    @ExcelProperty("授权时间")
    private String grantedAt;

    @ExcelProperty("解约时间")
    private String revokedAt;

    @ExcelProperty("来源")
    private String source;

    @ExcelProperty("解约原因")
    private String revokeReason;

    public ConsentExportRow() {
    }

    public ConsentExportRow(ConsentView record) {
        this.id = record.id();
        this.userId = record.userId();
        this.appId = record.appId();
        this.appName = record.appName() == null ? "" : record.appName();
        this.scopes = String.join(", ", record.scopes());
        this.grantedAt = DateUtils.format(DateUtils.fromEpochMilli(record.grantedAt()));
        this.revokedAt = Optional.ofNullable(record.revokedAt())
                .map(millis -> DateUtils.format(DateUtils.fromEpochMilli(millis))).orElse("");
        this.source = record.source();
        this.revokeReason = record.revokeReason();
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

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getScopes() {
        return scopes;
    }

    public void setScopes(String scopes) {
        this.scopes = scopes;
    }

    public String getGrantedAt() {
        return grantedAt;
    }

    public void setGrantedAt(String grantedAt) {
        this.grantedAt = grantedAt;
    }

    public String getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(String revokedAt) {
        this.revokedAt = revokedAt;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getRevokeReason() {
        return revokeReason;
    }

    public void setRevokeReason(String revokeReason) {
        this.revokeReason = revokeReason;
    }
}
