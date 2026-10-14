package cn.slainc.policy.support;

import cn.slainc.core.context.TenantContext;
import cn.slainc.core.enums.LoginType;
import cn.slainc.core.exception.BizException;
import cn.slainc.policy.domain.Policy;
import cn.slainc.policy.domain.PolicyBinding;
import cn.slainc.policy.domain.PolicyDocument;
import cn.slainc.policy.domain.PolicySubjectType;
import cn.slainc.policy.storage.PolicyBindingStore;

import java.util.List;

// 策略绑定编排：以既有策略文档为模板为主体创建独立策略实例并登记绑定镜像；Client 主体映射为 APP 体系下的 USER 型策略
public class PolicyBindingService {

    private final PolicyBindingStore bindingStore;

    private final PolicyAdminService adminService;

    public PolicyBindingService(PolicyBindingStore bindingStore, PolicyAdminService adminService) {
        this.bindingStore = bindingStore;
        this.adminService = adminService;
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

    public PolicyBinding bind(String sourcePolicyId, PolicySubjectType subjectType, String subjectId,
                              String loginType, String operator) {
        Policy source = adminService.get(sourcePolicyId);
        PolicyDocument document = source.getDocument();
        Policy created = adminService.create(source.getName() + "-" + subjectId, subjectType, subjectId,
                loginType, document, "由绑定操作从策略 " + source.getId() + " 派生", operator);
        PolicyBinding binding = new PolicyBinding();
        binding.setTenantId(TenantContext.getOrDefault());
        binding.setPolicyId(created.getId());
        binding.setSubjectType(subjectType == PolicySubjectType.ROLE
                ? PolicyBinding.SUBJECT_ROLE : PolicyBinding.SUBJECT_USER);
        if (subjectType == PolicySubjectType.ROLE) {
            binding.setSubjectId(subjectId);
        } else if (LoginType.APP.value().equals(loginType)) {
            binding.setSubjectType(PolicyBinding.SUBJECT_CLIENT);
            binding.setSubjectId(subjectId);
        } else {
            binding.setSubjectId(subjectId);
        }
        binding.setOperator(operator);
        binding.setCreateTime(System.currentTimeMillis());
        return bindingStore.insert(binding);
    }

    // 解绑删除派生策略本体与绑定镜像；源策略不受影响
    public void unbind(Long bindingId) {
        PolicyBinding binding = bindingStore.findById(bindingId);
        if (binding == null) {
            throw new BizException("绑定记录不存在: " + bindingId);
        }
        bindingStore.deleteById(bindingId);
        try {
            adminService.delete(binding.getPolicyId(), "binding-unbind");
        } catch (BizException e) {
            // 策略本体可能已被策略管理端删除，绑定镜像照常清理
        }
    }

    public List<PolicyBinding> query(String subjectType, String subjectId, String policyId) {
        return bindingStore.query(subjectType, subjectId, policyId);
    }
}
