package cn.slainc.modules.workorder.entity;

import cn.slainc.modules.workorder.WorkorderKeys;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;


@TableName(WorkOrderRecord.TABLE_NAME)
public class WorkOrderRecord {

    public static final String TABLE_NAME = WorkorderKeys.ORDER_TABLE;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String title;

    private String content;

    private String priority;

    private String status;

    private Long submitterId;

    private Long assigneeId;

    @TableField(value = "custom_fields")
    private String customFieldsJson;

    private Long createTime;

    private Long updateTime;

    private String number;

    private String typeCode;

    private String source;

    private String sourceSystem;

    private String sourceBizId;

    private Long parentOrderId;

    @TableField(value = "collaborator_ids")
    private String collaboratorIdsJson;

    private long version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getSubmitterId() {
        return submitterId;
    }

    public void setSubmitterId(Long submitterId) {
        this.submitterId = submitterId;
    }

    public Long getAssigneeId() {
        return assigneeId;
    }

    public void setAssigneeId(Long assigneeId) {
        this.assigneeId = assigneeId;
    }

    public String getCustomFieldsJson() {
        return customFieldsJson;
    }

    public void setCustomFieldsJson(String customFieldsJson) {
        this.customFieldsJson = customFieldsJson;
    }

    public Long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Long createTime) {
        this.createTime = createTime;
    }

    public Long getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Long updateTime) {
        this.updateTime = updateTime;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public String getTypeCode() {
        return typeCode;
    }

    public void setTypeCode(String typeCode) {
        this.typeCode = typeCode;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public void setSourceSystem(String sourceSystem) {
        this.sourceSystem = sourceSystem;
    }

    public String getSourceBizId() {
        return sourceBizId;
    }

    public void setSourceBizId(String sourceBizId) {
        this.sourceBizId = sourceBizId;
    }

    public Long getParentOrderId() {
        return parentOrderId;
    }

    public void setParentOrderId(Long parentOrderId) {
        this.parentOrderId = parentOrderId;
    }

    public String getCollaboratorIdsJson() {
        return collaboratorIdsJson;
    }

    public void setCollaboratorIdsJson(String collaboratorIdsJson) {
        this.collaboratorIdsJson = collaboratorIdsJson;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }
}
