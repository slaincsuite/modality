package cn.slainc.modules.policy.excel;

import cn.idev.excel.annotation.ExcelProperty;
import cn.slainc.modules.policy.dto.PolicyView;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class PolicyExportRow {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @ExcelProperty("策略名称")
    private String name;

    @ExcelProperty("作用对象类型")
    private String subjectType;

    @ExcelProperty("作用对象标识")
    private String subjectId;

    @ExcelProperty("账号体系")
    private String loginType;

    @ExcelProperty("分组")
    private String group;

    @ExcelProperty("说明")
    private String description;

    @ExcelProperty("策略文档")
    private String document;

    @ExcelProperty("创建时间")
    private String createTime;

    public PolicyExportRow() {
    }

    public PolicyExportRow(PolicyView view) {
        this.name = view.name();
        this.subjectType = view.subjectType();
        this.subjectId = view.subjectId();
        this.loginType = view.loginType();
        this.group = view.group();
        this.description = view.description();
        this.document = view.document();
        this.createTime = view.createdAt() <= 0 ? "" : LocalDateTime
                .ofInstant(Instant.ofEpochMilli(view.createdAt()), ZoneId.systemDefault()).format(TIME_FORMAT);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSubjectType() {
        return subjectType;
    }

    public void setSubjectType(String subjectType) {
        this.subjectType = subjectType;
    }

    public String getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(String subjectId) {
        this.subjectId = subjectId;
    }

    public String getLoginType() {
        return loginType;
    }

    public void setLoginType(String loginType) {
        this.loginType = loginType;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDocument() {
        return document;
    }

    public void setDocument(String document) {
        this.document = document;
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }
}
