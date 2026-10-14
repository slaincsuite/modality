package cn.slainc.modules.tenant.mapper;

import cn.slainc.modules.tenant.entity.TenantPlanRecord;
import cn.slainc.mybatis.tenant.IgnoreTenant;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

// 套餐模板为平台级数据，不做租户行隔离
@IgnoreTenant
public interface TenantPlanMapper extends BaseMapper<TenantPlanRecord> {
}
