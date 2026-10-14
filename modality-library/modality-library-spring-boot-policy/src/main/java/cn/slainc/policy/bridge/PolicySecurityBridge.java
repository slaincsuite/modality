package cn.slainc.policy.bridge;

import cn.slainc.core.context.TenantContext;
import cn.slainc.core.enums.LoginType;
import cn.slainc.policy.engine.PolicyEngine;
import cn.slainc.policy.domain.PolicyRequest;
import cn.slainc.security.spi.SecurityPolicyProvider;

import java.util.List;

public class PolicySecurityBridge implements SecurityPolicyProvider {

    private final PolicyEngine engine;

    public PolicySecurityBridge(PolicyEngine engine) {
        this.engine = engine;
    }

    @Override
    public List<String> getPermissions(Object loginId, LoginType type) {
        PolicyRequest request = new PolicyRequest();
        request.userId(String.valueOf(loginId));
        request.loginType(type.name());
        request.setTenantId(TenantContext.get());
        return engine.resolveActions(request);
    }

    @Override
    public List<String> getRoles(Object loginId, LoginType type) {
        return List.of();
    }
}
