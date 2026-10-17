package cn.slainc.modules.upms.mapper;

import cn.slainc.modules.upms.entity.UserSettingsRecord;
import cn.slainc.mybatis.tenant.IgnoreTenant;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

@IgnoreTenant
public interface UserSettingsMapper extends BaseMapper<UserSettingsRecord> {
}
