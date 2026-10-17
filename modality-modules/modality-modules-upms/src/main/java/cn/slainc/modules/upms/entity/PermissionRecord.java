package cn.slainc.modules.upms.entity;

import cn.slainc.modules.upms.UpmsKeys;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.util.List;

@TableName(PermissionRecord.TABLE_NAME)
public class PermissionRecord {

    public static final String TABLE_NAME = UpmsKeys.PERMISSION_TABLE;

    public static final String TYPE_MENU = "MENU";

    public static final String TYPE_BUTTON = "BUTTON";

    public static final String TYPE_API = "API";

    public static final String TYPE_DATA = "DATA";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String code;

    private String name;

    private String type = TYPE_MENU;

    private Long parentId = 0L;

    private Integer sort = 0;

    private String status = UpmsKeys.STATUS_ENABLED;

    private String remark;

    private Long createTime;

    private Long updateTime;

    @TableField(exist = false)
    private List<PermissionRecord> children;

    public boolean enabled() {
        return UpmsKeys.STATUS_ENABLED.equals(status);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public Integer getSort() {
        return sort;
    }

    public void setSort(Integer sort) {
        this.sort = sort;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
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

    public List<PermissionRecord> getChildren() {
        return children;
    }

    public void setChildren(List<PermissionRecord> children) {
        this.children = children;
    }
}
