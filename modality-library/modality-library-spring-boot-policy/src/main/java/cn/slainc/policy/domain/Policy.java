package cn.slainc.policy.domain;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;

import java.security.SecureRandom;
import java.util.HexFormat;

public class Policy {

    private static final SecureRandom RANDOM = new SecureRandom();

    private String id;

    private String name;

    private String description = "";

    private PolicySubjectType subjectType;

    private String subjectId;

    private String loginType;

    private PolicyStatus status = PolicyStatus.ENABLED;

    private int version = 1;

    private PolicyDocument document;

    private String creator;

    private long createdAt;

    private long updatedAt;

    public static Policy create(String name, PolicySubjectType subjectType, String subjectId, String loginType,
                                PolicyDocument document) {
        if (name == null || name.isBlank()) {
            throw new BizException(ResultCode.PARAM_MISSING, "策略名称不能为空");
        }
        if (subjectType == null) {
            throw new BizException(ResultCode.PARAM_MISSING, "策略作用对象不能为空");
        }
        if (subjectType != PolicySubjectType.GLOBAL && (subjectId == null || subjectId.isBlank())) {
            throw new BizException(ResultCode.PARAM_MISSING, subjectType.code() + " 类型策略必须指定作用对象标识");
        }
        if (document == null) {
            throw new BizException(ResultCode.PARAM_MISSING, "策略文档不能为空");
        }
        document.validate();
        Policy policy = new Policy();
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        policy.setId("pol_" + HexFormat.of().formatHex(bytes));
        policy.setName(name.trim());
        policy.setSubjectType(subjectType);
        policy.setSubjectId(subjectId == null ? null : subjectId.trim());
        policy.setLoginType(loginType == null || loginType.isBlank() ? null : loginType.trim().toUpperCase());
        policy.setDocument(document);
        return policy;
    }

    public boolean enabled() {
        return status == PolicyStatus.ENABLED;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public PolicySubjectType getSubjectType() {
        return subjectType;
    }

    public void setSubjectType(PolicySubjectType subjectType) {
        this.subjectType = subjectType;
    }

    public void setSubjectType(String subjectType) {
        this.subjectType = PolicySubjectType.of(subjectType);
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

    public PolicyStatus getStatus() {
        return status;
    }

    public void setStatus(PolicyStatus status) {
        this.status = status;
    }

    public void setStatus(String status) {
        this.status = PolicyStatus.of(status);
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public PolicyDocument getDocument() {
        return document;
    }

    public void setDocument(PolicyDocument document) {
        this.document = document;
    }

    public String getCreator() {
        return creator;
    }

    public void setCreator(String creator) {
        this.creator = creator;
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
