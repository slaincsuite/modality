package cn.slainc.modules.upms.service;

import cn.slainc.core.context.TenantContext;
import cn.slainc.core.enums.LoginType;
import cn.slainc.core.exception.BizException;
import cn.slainc.modules.upms.entity.PolicyBindingRecord;
import cn.slainc.modules.upms.mapper.PolicyBindingMapper;
import cn.slainc.policy.admin.PolicyAdminService;
import cn.slainc.policy.model.Policy;
import cn.slainc.policy.model.PolicyDocument;
import cn.slainc.policy.model.PolicySubjectType;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// 策略绑定：以既有策略文档为模板，为主体创建独立策略实例并登记绑定镜像；Client 主体映射为 APP 体系下的 USER 型策略
public class PolicyBindingService {

    private final PolicyBindingMapper bindingMapper;

    private final ObjectProvider<PolicyAdminService> policyAdminService;

    public PolicyBindingService(PolicyBindingMapper bindingMapper,
                                ObjectProvider<PolicyAdminService> policyAdminService) {
        this.bindingMapper = bindingMapper;
        this.policyAdminService = policyAdminService;
    }

    private PolicyAdminService adminService() {
        PolicyAdminService service = policyAdminService.getIfAvailable();
        if (service == null) {
            throw new BizException("策略引擎未装配，无法绑定策略");
        }
        return service;
    }

    public PolicyBindingRecord bindToUser(String sourcePolicyId, Long userId, LoginType loginType, String operator) {
        if (loginType == null) {
            throw new BizException("绑定用户策略必须指定账号体系");
        }
        return bind(sourcePolicyId, PolicySubjectType.USER, String.valueOf(userId), loginType.value(), operator);
    }

    public PolicyBindingRecord bindToRole(String sourcePolicyId, String roleCode, String operator) {
        if (roleCode == null || roleCode.isBlank()) {
            throw new BizException("绑定角色策略必须提供角色编码");
        }
        return bind(sourcePolicyId, PolicySubjectType.ROLE, roleCode.trim(), null, operator);
    }

    public PolicyBindingRecord bindToClient(String sourcePolicyId, Long clientId, String operator) {
        if (clientId == null) {
            throw new BizException("绑定 Client 策略必须提供客户端标识");
        }
        return bind(sourcePolicyId, PolicySubjectType.USER, String.valueOf(clientId), LoginType.APP.value(), operator);
    }

    @Transactional
    public PolicyBindingRecord bind(String sourcePolicyId, PolicySubjectType subjectType, String subjectId,
                                    String loginType, String operator) {
        Policy source = adminService().get(sourcePolicyId);
        PolicyDocument document = source.getDocument();
        Policy created = adminService().create(source.getName() + "-" + subjectId, subjectType, subjectId,
                loginType, document, "由绑定操作从策略 " + source.getId() + " 派生", operator);
        PolicyBindingRecord binding = new PolicyBindingRecord();
        binding.setTenantId(TenantContext.getOrDefault());
        binding.setPolicyId(created.getId());
        binding.setSubjectType(subjectType == PolicySubjectType.ROLE
                ? PolicyBindingRecord.SUBJECT_ROLE : PolicyBindingRecord.SUBJECT_USER);
        if (subjectType == PolicySubjectType.ROLE) {
            binding.setSubjectId(subjectId);
        } else if (LoginType.APP.value().equals(loginType)) {
            binding.setSubjectType(PolicyBindingRecord.SUBJECT_CLIENT);
            binding.setSubjectId(subjectId);
        } else {
            binding.setSubjectId(subjectId);
        }
        binding.setOperator(operator);
        binding.setCreateTime(System.currentTimeMillis());
        bindingMapper.insert(binding);
        return binding;
    }

    // 解绑删除派生策略本体与绑定镜像；源策略不受影响
    @Transactional
    public void unbind(Long bindingId) {
        PolicyBindingRecord binding = bindingMapper.selectById(bindingId);
        if (binding == null) {
            throw new BizException("绑定记录不存在: " + bindingId);
        }
        bindingMapper.deleteById(bindingId);
        try {
            adminService().delete(binding.getPolicyId(), "upms-unbind");
        } catch (BizException e) {
            // 策略本体可能已被策略管理端删除，绑定镜像照常清理
        }
    }

    public List<PolicyBindingRecord> query(String subjectType, String subjectId, String policyId) {
        return bindingMapper.selectList(new LambdaQueryWrapper<PolicyBindingRecord>()
                .eq(subjectType != null && !subjectType.isBlank(), PolicyBindingRecord::getSubjectType, subjectType)
                .eq(subjectId != null && !subjectId.isBlank(), PolicyBindingRecord::getSubjectId, subjectId)
                .eq(policyId != null && !policyId.isBlank(), PolicyBindingRecord::getPolicyId, policyId)
                .orderByDesc(PolicyBindingRecord::getId));
    }
}
