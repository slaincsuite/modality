package cn.slainc.apikey.mapper;

import cn.slainc.apikey.ApiKeyRecord;
import cn.slainc.mybatis.tenant.IgnoreTenant;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

@IgnoreTenant
public interface ApiKeyMapper extends BaseMapper<ApiKeyRecord> {
}
