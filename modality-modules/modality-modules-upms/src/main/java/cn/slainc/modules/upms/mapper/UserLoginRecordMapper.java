package cn.slainc.modules.upms.mapper;

import cn.slainc.modules.upms.entity.UserLoginRecordRecord;
import cn.slainc.mybatis.tenant.IgnoreTenant;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

@IgnoreTenant
public interface UserLoginRecordMapper extends BaseMapper<UserLoginRecordRecord> {
}
