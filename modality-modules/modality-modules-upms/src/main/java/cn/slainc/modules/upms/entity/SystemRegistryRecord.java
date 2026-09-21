package cn.slainc.modules.upms.entity;

import cn.slainc.modules.upms.UpmsKeys;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName(SystemRegistryRecord.TABLE_NAME)
public class SystemRegistryRecord {

    public static final String TABLE_NAME = UpmsKeys.SYSTEM_REGISTRY_TABLE;

    public static final String VISIBILITY_PUBLIC = "PUBLIC";

    public static final String VISIBILITY_PRIVATE = "PRIVATE";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String code;

    private String name;

    private String moduleCode;

    private String visibility = VISIBILITY_PUBLIC;

    private String groupName;

    private String icon;

    private String url;

    private Integer sort = 0;

    private String status = UpmsKeys.STATUS_ENABLED;

    private Long createTime;

    private Long updateTime;

    public boolean enabled() {
        return UpmsKeys.STATUS_ENABLED.equals(status);
    }

    public String accessPermissionCode() {
        return moduleCode + ":" + code + UpmsKeys.ACCESS_PERMISSION_SUFFIX;
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

    public String getModuleCode() {
        return moduleCode;
    }

    public void setModuleCode(String moduleCode) {
        this.moduleCode = moduleCode;
    }

    public String getVisibility() {
        return visibility;
    }

    public void setVisibility(String visibility) {
        this.visibility = visibility;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
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
}
