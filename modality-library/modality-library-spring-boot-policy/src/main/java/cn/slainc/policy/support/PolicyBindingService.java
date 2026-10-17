package cn.slainc.policy.support;

import cn.slainc.core.context.TenantContext;
import cn.slainc.core.enums.LoginType;
import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;
import cn.slainc.policy.domain.Policy;
import cn.slainc.policy.domain.PolicyBinding;
import cn.slainc.policy.domain.PolicyDocument;
import cn.slainc.policy.domain.PolicySubjectType;
import cn.slainc.policy.storage.PolicyBindingOrigin;
import cn.slainc.policy.storage.PolicyBindingOriginStore;
import cn.slainc.policy.storage.PolicyBindingStore;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// 策略绑定编排：以既有策略文档为模板为主体创建独立策略实例并登记绑定镜像；Client 主体映射为 APP 体系下的 USER 型策略
public class PolicyBindingService {

    public static final String DERIVED_DESCRIPTION_PREFIX = "由绑定操作从策略 ";

    private final PolicyBindingStore bindingStore;

    private final PolicyAdminService adminService;

    private final ObjectProvider<PolicyBindingOriginStore> originStore;

    public PolicyBindingService(PolicyBindingStore bindingStore, PolicyAdminService adminService) {
        this(bindingStore, adminService, null);
    }

    public PolicyBindingService(PolicyBindingStore bindingStore, PolicyAdminService adminService,
                                ObjectProvider<PolicyBindingOriginStore> originStore) {
        this.bindingStore = bindingStore;
        this.adminService = adminService;
        this.originStore = originStore;
    }

    public PolicyBinding bindToUser(String sourcePolicyId, Long userId, LoginType loginType, String operator) {
        if (loginType == null) {
            throw new BizException("绑定用户策略必须指定账号体系");
        }
        return bind(sourcePolicyId, PolicySubjectType.USER, String.valueOf(userId), loginType.value(), operator);
    }

    public PolicyBinding bindToRole(String sourcePolicyId, String roleCode, String operator) {
        if (roleCode == null || roleCode.isBlank()) {
            throw new BizException("绑定角色策略必须提供角色编码");
        }
        return bind(sourcePolicyId, PolicySubjectType.ROLE, roleCode.trim(), null, operator);
    }

    public PolicyBinding bindToClient(String sourcePolicyId, Long clientId, String operator) {
        if (clientId == null) {
            throw new BizException("绑定 Client 策略必须提供客户端标识");
        }
        return bind(sourcePolicyId, PolicySubjectType.USER, String.valueOf(clientId), LoginType.APP.value(), operator);
    }

    public PolicyBinding bindToGroup(String sourcePolicyId, String groupId, String operator) {
        if (groupId == null || groupId.isBlank()) {
            throw new BizException("绑定用户组策略必须提供用户组标识");
        }
        return bind(sourcePolicyId, PolicySubjectType.GROUP, groupId.trim(), null, operator);
    }

    public PolicyBinding bind(String sourcePolicyId, PolicySubjectType subjectType, String subjectId,
                              String loginType, String operator) {
        Policy source = adminService.get(sourcePolicyId);
        String bindingType = bindingType(subjectType, loginType);
        Long tenantId = TenantContext.getOrDefault();
        PolicyBindingOriginStore origins = originStore();
        if (origins != null) {
            Optional<PolicyBindingOrigin> existing = origins.find(sourcePolicyId, bindingType, subjectId, tenantId);
            if (existing.isPresent()) {
                PolicyBinding binding = bindingStore.findById(existing.get().bindingId());
                if (binding != null) {
                    binding.setSourcePolicyId(sourcePolicyId);
                    return binding;
                }
                origins.deleteByBindingId(existing.get().bindingId());
            }
        }
        PolicyDocument document = source.getDocument();
        PolicySubjectType boundSubjectType = subjectType;
        String boundSubjectId = subjectId;
        if (subjectType == PolicySubjectType.ROLE) {
            boundSubjectType = PolicySubjectType.TENANT_ROLE;
            boundSubjectId = tenantId + ":" + subjectId;
        }
        if (subjectType == PolicySubjectType.GROUP) {
            boundSubjectType = PolicySubjectType.TENANT_GROUP;
            boundSubjectId = tenantId + ":" + subjectId;
        }
        if (boundSubjectId.length() > 64) {
            throw new BizException(ResultCode.PARAM_BIND_ERROR,
                    "角色或用户组编码过长，绑定后的策略主体标识最多 64 个字符");
        }
        Policy created = adminService.create(source.getName() + "-" + subjectId, boundSubjectType, boundSubjectId,
                loginType, document, DERIVED_DESCRIPTION_PREFIX + source.getId() + " 派生", operator);
        PolicyBinding binding = new PolicyBinding();
        binding.setTenantId(tenantId);
        binding.setPolicyId(created.getId());
        binding.setSourcePolicyId(sourcePolicyId);
        binding.setSubjectType(bindingType);
        binding.setSubjectId(subjectId);
        binding.setOperator(operator);
        binding.setCreateTime(System.currentTimeMillis());
        PolicyBinding saved = bindingStore.insert(binding);
        if (origins != null) {
            origins.save(new PolicyBindingOrigin(saved.getId(), saved.getPolicyId(), sourcePolicyId,
                    bindingType, subjectId, tenantId, saved.getCreateTime()));
        }
        return saved;
    }

    // 解绑删除派生策略本体与绑定镜像；源策略不受影响
    public void unbind(Long bindingId) {
        PolicyBinding binding = bindingStore.findById(bindingId);
        if (binding == null) {
            throw new BizException("绑定记录不存在: " + bindingId);
        }
        PolicyBindingOriginStore origins = originStore();
        if (origins != null) {
            origins.deleteByBindingId(bindingId);
        }
        bindingStore.deleteById(bindingId);
        try {
            adminService.delete(binding.getPolicyId(), "binding-unbind");
        } catch (BizException e) {
            // 策略本体可能已被策略管理端删除，绑定镜像照常清理
        }
    }

    public List<PolicyBinding> query(String subjectType, String subjectId, String policyId) {
        return bindingStore.query(subjectType, subjectId, policyId).stream()
                .map(this::withSourcePolicy)
                .toList();
    }

    public List<PolicyBinding> queryBySource(String sourcePolicyId) {
        PolicyBindingOriginStore origins = originStore();
        if (origins == null || sourcePolicyId == null || sourcePolicyId.isBlank()) {
            return List.of();
        }
        List<PolicyBinding> bindings = new ArrayList<>();
        for (PolicyBindingOrigin origin : origins.listBySource(sourcePolicyId, TenantContext.getOrDefault())) {
            PolicyBinding binding = bindingStore.findById(origin.bindingId());
            if (binding != null) {
                binding.setSourcePolicyId(sourcePolicyId);
                bindings.add(binding);
            }
        }
        return bindings;
    }

    private String bindingType(PolicySubjectType subjectType, String loginType) {
        if (subjectType == PolicySubjectType.ROLE) {
            return PolicyBinding.SUBJECT_ROLE;
        }
        if (subjectType == PolicySubjectType.GROUP) {
            return PolicyBinding.SUBJECT_GROUP;
        }
        if (LoginType.APP.value().equals(loginType)) {
            return PolicyBinding.SUBJECT_CLIENT;
        }
        return PolicyBinding.SUBJECT_USER;
    }

    private PolicyBindingOriginStore originStore() {
        return originStore == null ? null : originStore.getIfAvailable();
    }

    private PolicyBinding withSourcePolicy(PolicyBinding binding) {
        if (binding == null || binding.getSourcePolicyId() != null) {
            return binding;
        }
        PolicyBindingOriginStore origins = originStore();
        if (origins == null) {
            return binding;
        }
        origins.findByPolicyId(binding.getPolicyId(), TenantContext.getOrDefault())
                .ifPresent(origin -> binding.setSourcePolicyId(origin.sourcePolicyId()));
        return binding;
    }
}
