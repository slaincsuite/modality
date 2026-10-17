package cn.slainc.modules.upms.security;

import cn.slainc.core.context.TenantContext;
import cn.slainc.core.enums.LoginType;
import cn.slainc.modules.upms.entity.UserRecord;
import cn.slainc.modules.upms.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import java.util.List;

/**
 * Resolves a login identity as either the exact username or a phone number.
 * Phone matches must be unique inside the selected tenant and login type.
 */
public final class UpmsLoginIdentityLookup {

    private UpmsLoginIdentityLookup() {
    }

    public static UserRecord resolve(UserMapper userMapper, String loginIdentity, LoginType type) {
        if (userMapper == null || loginIdentity == null || loginIdentity.isBlank()
                || type == null || type == LoginType.GUEST) {
            return null;
        }
        String identity = loginIdentity.trim();
        UserRecord byUsername = userMapper.selectOne(scoped(type)
                .eq(UserRecord::getUsername, identity)
                .last("LIMIT 1"));
        if (byUsername != null) {
            return byUsername;
        }
        List<UserRecord> byPhone = userMapper.selectList(scoped(type)
                .eq(UserRecord::getPhone, identity)
                .last("LIMIT 2"));
        return byPhone.size() == 1 ? byPhone.get(0) : null;
    }

    private static LambdaQueryWrapper<UserRecord> scoped(LoginType type) {
        LambdaQueryWrapper<UserRecord> wrapper = new LambdaQueryWrapper<UserRecord>()
                .eq(UserRecord::getLoginType, type.value())
                .eq(UserRecord::getDeleted, false);
        Long tenantId = TenantContext.get();
        if (tenantId != null) {
            wrapper.eq(UserRecord::getTenantId, tenantId);
        }
        return wrapper;
    }
}
