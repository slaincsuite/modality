package cn.slainc.tenant.dao;

import cn.slainc.mybatis.tenant.IgnoreTenant;
import cn.slainc.tenant.domain.Tenant;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

@IgnoreTenant
public interface TenantMapper extends BaseMapper<Tenant> {
}
