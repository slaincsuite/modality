package cn.slainc.modules.tenant.entity;

import cn.slainc.modules.tenant.TenantKeys;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

// 套餐自带的默认菜单范围；modality 用户菜单为平台级表，此范围仅做模板存取与生效值输出，不写租户授权
@TableName(TenantPlanMenuRecord.TABLE_NAME)
public class TenantPlanMenuRecord {

    public static final String TABLE_NAME = TenantKeys.PLAN_MENU_TABLE;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long planId;

    private Long menuId;

    private Long createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPlanId() {
        return planId;
    }

    public void setPlanId(Long planId) {
        this.planId = planId;
    }

    public Long getMenuId() {
        return menuId;
    }

    public void setMenuId(Long menuId) {
        this.menuId = menuId;
    }

    public Long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Long createTime) {
        this.createTime = createTime;
    }
}
