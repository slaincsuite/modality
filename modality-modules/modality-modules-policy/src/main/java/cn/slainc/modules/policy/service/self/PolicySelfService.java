package cn.slainc.modules.policy.service.self;

import cn.slainc.core.context.UserContext;
import cn.slainc.core.enums.LoginType;
import cn.slainc.modules.policy.dto.MyPolicyView;
import cn.slainc.modules.policy.dto.PolicyView;
import cn.slainc.modules.policy.service.binding.PolicyBindingPortalService;
import cn.slainc.modules.policy.service.manage.PolicyManageService;
import cn.slainc.policy.domain.PolicyBinding;
import cn.slainc.policy.spi.PolicyGroupProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PolicySelfService {

    private static final Logger log = LoggerFactory.getLogger(PolicySelfService.class);

    private final PolicyManageService manageService;

    private final PolicyBindingPortalService bindingService;

    private final ObjectProvider<PolicyGroupProvider> groupProvider;

    public PolicySelfService(PolicyManageService manageService, PolicyBindingPortalService bindingService,
                             ObjectProvider<PolicyGroupProvider> groupProvider) {
        this.manageService = manageService;
        this.bindingService = bindingService;
        this.groupProvider = groupProvider;
    }

    public MyPolicyView policies(UserContext.UserInfo user) {
        List<PolicyView> direct = directPolicies(user);
        List<PolicyView> roles = rolePolicies(user);
        List<PolicyView> groups = groupPolicies(user);
        Map<String, PolicyView> all = new LinkedHashMap<>();
        direct.forEach(policy -> all.putIfAbsent(policy.id(), policy));
        roles.forEach(policy -> all.putIfAbsent(policy.id(), policy));
        groups.forEach(policy -> all.putIfAbsent(policy.id(), policy));
        return new MyPolicyView(List.copyOf(direct), List.copyOf(roles), List.copyOf(groups), List.copyOf(all.values()));
    }

    private List<PolicyView> directPolicies(UserContext.UserInfo user) {
        Map<String, PolicyView> result = new LinkedHashMap<>();
        addSubjectPolicies(result, "user", String.valueOf(user.userId()), user.loginType());
        addBindingPolicies(result, "USER", String.valueOf(user.userId()), user.loginType());
        if (user.loginType() == LoginType.APP) {
            addBindingPolicies(result, "CLIENT", String.valueOf(user.userId()), user.loginType());
        }
        return new ArrayList<>(result.values());
    }

    private List<PolicyView> rolePolicies(UserContext.UserInfo user) {
        Map<String, PolicyView> result = new LinkedHashMap<>();
        for (String role : user.roles()) {
            addSubjectPolicies(result, "role", role, user.loginType());
            addBindingPolicies(result, "ROLE", role, user.loginType());
        }
        return new ArrayList<>(result.values());
    }

    private List<PolicyView> groupPolicies(UserContext.UserInfo user) {
        Map<String, PolicyView> result = new LinkedHashMap<>();
        for (String group : groups(user)) {
            addSubjectPolicies(result, "group", group, user.loginType());
            addBindingPolicies(result, "GROUP", group, user.loginType());
        }
        return new ArrayList<>(result.values());
    }

    private void addSubjectPolicies(Map<String, PolicyView> target, String subjectType, String subjectId,
                                    LoginType loginType) {
        manageService.page(null, subjectType, subjectId, null, null, null, 1, Integer.MAX_VALUE)
                .records().stream()
                .filter(policy -> policy.loginType() == null || policy.loginType().isBlank()
                        || loginType == null || policy.loginType().equalsIgnoreCase(loginType.value()))
                .forEach(policy -> target.putIfAbsent(policy.id(), policy));
    }

    private void addBindingPolicies(Map<String, PolicyView> target, String subjectType, String subjectId,
                                    LoginType loginType) {
        List<PolicyBinding> bindings = bindingService.query(subjectType, subjectId, null);
        for (PolicyBinding binding : bindings) {
            try {
                PolicyView policy = manageService.view(binding.getPolicyId());
                if (policy.loginType() == null || policy.loginType().isBlank()
                        || loginType == null || policy.loginType().equalsIgnoreCase(loginType.value())) {
                    target.putIfAbsent(policy.id(), policy);
                }
            } catch (RuntimeException exception) {
                log.warn("读取主体策略 {} 失败: {}", binding.getPolicyId(), exception.getMessage());
            }
        }
    }

    private List<String> groups(UserContext.UserInfo user) {
        PolicyGroupProvider provider = groupProvider.getIfAvailable();
        if (provider == null) {
            return List.of();
        }
        try {
            List<String> groups = provider.groups(user.userId(), user.loginType());
            return groups == null ? List.of() : groups.stream()
                    .filter(group -> group != null && !group.isBlank())
                    .map(String::trim)
                    .distinct()
                    .toList();
        } catch (RuntimeException exception) {
            log.warn("解析当前账号用户组失败: {}", exception.getMessage());
            return List.of();
        }
    }
}
