package cn.slainc.modules.upms.service.user;

import cn.slainc.core.context.TenantContext;
import cn.slainc.core.spi.UserUsage;
import cn.slainc.core.spi.UserUsageProvider;
import cn.slainc.modules.upms.UpmsKeys;
import cn.slainc.modules.upms.entity.UserRecord;
import cn.slainc.modules.upms.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

public class UpmsUserUsageProvider implements UserUsageProvider {

    private final UserMapper userMapper;

    public UpmsUserUsageProvider(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public UserUsage usage(Long tenantId) {
        Long effectiveTenant = tenantId == null ? TenantContext.getOrDefault() : tenantId;
        if (tenantId == null) {
            return usageInCurrentTenant();
        }
        return TenantContext.callWith(effectiveTenant, this::usageInCurrentTenant);
    }

    private UserUsage usageInCurrentTenant() {
        long total = userMapper.selectCount(new LambdaQueryWrapper<UserRecord>()
                .eq(UserRecord::getDeleted, false));
        long active = userMapper.selectCount(new LambdaQueryWrapper<UserRecord>()
                .eq(UserRecord::getDeleted, false)
                .eq(UserRecord::getStatus, UpmsKeys.STATUS_ENABLED)
                .eq(UserRecord::getConsoleAccess, true));
        return new UserUsage(total, active, Math.max(0L, total - active));
    }
}
