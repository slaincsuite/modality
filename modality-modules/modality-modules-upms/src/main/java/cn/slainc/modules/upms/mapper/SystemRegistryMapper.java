package cn.slainc.modules.upms.mapper;

import cn.slainc.modules.upms.entity.SystemRegistryRecord;
import cn.slainc.mybatis.tenant.IgnoreTenant;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

@IgnoreTenant
public interface SystemRegistryMapper extends BaseMapper<SystemRegistryRecord> {
}
