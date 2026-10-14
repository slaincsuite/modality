package cn.slainc.modules.tenant.mapper;

import cn.slainc.modules.tenant.entity.TenantPlanMenuRecord;
import cn.slainc.mybatis.tenant.IgnoreTenant;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

// 套餐默认菜单为平台级数据，不做租户行隔离
@IgnoreTenant
public interface TenantPlanMenuMapper extends BaseMapper<TenantPlanMenuRecord> {
}
