package cn.slainc.modules.audit.mapper;

import cn.slainc.modules.audit.AuditKeys;
import cn.slainc.modules.audit.entity.ApiLogRecord;
import cn.slainc.mybatis.tenant.IgnoreTenant;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;


@IgnoreTenant
public interface ApiLogMapper extends BaseMapper<ApiLogRecord> {
}
