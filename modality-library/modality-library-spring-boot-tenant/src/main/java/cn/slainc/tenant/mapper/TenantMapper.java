package cn.slainc.tenant.mapper;

import cn.slainc.mybatis.tenant.IgnoreTenant;
import cn.slainc.tenant.entity.Tenant;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

@IgnoreTenant
public interface TenantMapper extends BaseMapper<Tenant> {
}
