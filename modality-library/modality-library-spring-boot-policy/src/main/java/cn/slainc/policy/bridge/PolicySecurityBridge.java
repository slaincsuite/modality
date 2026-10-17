package cn.slainc.policy.bridge;

import cn.slainc.core.context.TenantContext;
import cn.slainc.core.enums.LoginType;
import cn.slainc.policy.engine.PolicyEngine;
import cn.slainc.policy.domain.PolicyRequest;
import cn.slainc.policy.spi.PolicyGroupProvider;
import cn.slainc.security.spi.SecurityPolicyProvider;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

public class PolicySecurityBridge implements SecurityPolicyProvider {

    private final PolicyEngine engine;

    private final ObjectProvider<PolicyGroupProvider> groupProvider;

    public PolicySecurityBridge(PolicyEngine engine) {
        this(engine, null);
    }

    public PolicySecurityBridge(PolicyEngine engine, ObjectProvider<PolicyGroupProvider> groupProvider) {
        this.engine = engine;
        this.groupProvider = groupProvider;
    }

    @Override
    public List<String> getPermissions(Object loginId, LoginType type) {
        PolicyRequest request = new PolicyRequest();
        request.userId(String.valueOf(loginId));
        request.loginType(type.name());
        request.setTenantId(TenantContext.get());
        PolicyGroupProvider provider = groupProvider == null ? null : groupProvider.getIfAvailable();
        request.setGroups(provider == null ? List.of() : provider.groups(loginId, type));
        return engine.resolveActions(request);
    }

    @Override
    public List<String> getRoles(Object loginId, LoginType type) {
        return List.of();
    }
}
