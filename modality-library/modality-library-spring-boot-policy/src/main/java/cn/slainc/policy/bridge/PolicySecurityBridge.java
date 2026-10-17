package cn.slainc.policy.bridge;

import cn.slainc.core.context.TenantContext;
import cn.slainc.core.enums.LoginType;
import cn.slainc.policy.engine.PolicyEngine;
import cn.slainc.policy.domain.PolicyRequest;
import cn.slainc.policy.spi.PolicyGroupProvider;
import cn.slainc.security.spi.SecurityPolicyProvider;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.LinkedHashSet;
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
        request.setTenantId(TenantContext.getOrDefault());
        PolicyGroupProvider provider = groupProvider == null ? null : groupProvider.getIfAvailable();
        request.setGroups(provider == null ? List.of() : provider.groups(loginId, type));
        LinkedHashSet<String> permissions = new LinkedHashSet<>();
        for (String action : engine.resolveActions(request)) {
            permissions.add(action);
            if (action.startsWith("modality:")) {
                permissions.add(action.substring("modality:".length()));
            }
        }
        for (String action : engine.resolveDeniedActions(request)) {
            permissions.add("!" + action);
            if (action.startsWith("modality:")) {
                permissions.add("!" + action.substring("modality:".length()));
            }
        }
        return new ArrayList<>(permissions);
    }

    @Override
    public List<String> getRoles(Object loginId, LoginType type) {
        return List.of();
    }
}
