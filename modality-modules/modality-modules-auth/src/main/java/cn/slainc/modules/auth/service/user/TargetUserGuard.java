package cn.slainc.modules.auth.service.user;

import cn.slainc.core.context.TenantContext;
import cn.slainc.core.context.UserContext;
import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;
import cn.slainc.security.spi.AccountAdminPort;
import org.springframework.beans.factory.ObjectProvider;

public class TargetUserGuard {

    private final ObjectProvider<AccountAdminPort> accountAdminPort;

    public TargetUserGuard(ObjectProvider<AccountAdminPort> accountAdminPort) {
        this.accountAdminPort = accountAdminPort;
    }

    public void require(Long userId) {
        if (userId == null) {
            throw new BizException(ResultCode.PARAM_MISSING, "用户标识不能为空");
        }
        AccountAdminPort port = accountAdminPort == null ? null : accountAdminPort.getIfAvailable();
        if (port == null || UserContext.isSuperAdmin()) {
            return;
        }
        Long targetTenant;
        try {
            targetTenant = port.tenantIdOf(userId);
        } catch (RuntimeException e) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "目标用户不存在");
        }
        if (targetTenant != null && !targetTenant.equals(TenantContext.getOrDefault())) {
            throw new BizException(ResultCode.FORBIDDEN, "无权操作其他租户的用户");
        }
    }
}
