package cn.slainc.modules.upms.service;

import cn.slainc.modules.upms.entity.PolicyBindingRecord;
import cn.slainc.modules.upms.mapper.PolicyBindingMapper;
import cn.slainc.policy.model.PolicyBinding;
import cn.slainc.policy.storage.PolicyBindingStore;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import java.util.List;

// t_policy_binding 的存储实现：接 policy 库的绑定编排，镜像记录的落库与查询都走本模块 mapper
public class UpmsPolicyBindingStore implements PolicyBindingStore {

    private final PolicyBindingMapper bindingMapper;

    public UpmsPolicyBindingStore(PolicyBindingMapper bindingMapper) {
        this.bindingMapper = bindingMapper;
    }

    @Override
    public PolicyBinding insert(PolicyBinding binding) {
        PolicyBindingRecord record = toRecord(binding);
        bindingMapper.insert(record);
        binding.setId(record.getId());
        return binding;
    }

    @Override
    public PolicyBinding findById(Long id) {
        return toModel(bindingMapper.selectById(id));
    }

    @Override
    public void deleteById(Long id) {
        bindingMapper.deleteById(id);
    }

    @Override
    public List<PolicyBinding> query(String subjectType, String subjectId, String policyId) {
        return bindingMapper.selectList(new LambdaQueryWrapper<PolicyBindingRecord>()
                .eq(subjectType != null && !subjectType.isBlank(), PolicyBindingRecord::getSubjectType, subjectType)
                .eq(subjectId != null && !subjectId.isBlank(), PolicyBindingRecord::getSubjectId, subjectId)
                .eq(policyId != null && !policyId.isBlank(), PolicyBindingRecord::getPolicyId, policyId)
                .orderByDesc(PolicyBindingRecord::getId))
                .stream().map(this::toModel).toList();
    }

    private PolicyBindingRecord toRecord(PolicyBinding binding) {
        PolicyBindingRecord record = new PolicyBindingRecord();
        record.setId(binding.getId());
        record.setTenantId(binding.getTenantId());
        record.setPolicyId(binding.getPolicyId());
        record.setSubjectType(binding.getSubjectType());
        record.setSubjectId(binding.getSubjectId());
        record.setOperator(binding.getOperator());
        record.setCreateTime(binding.getCreateTime());
        return record;
    }

    private PolicyBinding toModel(PolicyBindingRecord record) {
        if (record == null) {
            return null;
        }
        PolicyBinding binding = new PolicyBinding();
        binding.setId(record.getId());
        binding.setTenantId(record.getTenantId());
        binding.setPolicyId(record.getPolicyId());
        binding.setSubjectType(record.getSubjectType());
        binding.setSubjectId(record.getSubjectId());
        binding.setOperator(record.getOperator());
        binding.setCreateTime(record.getCreateTime());
        return binding;
    }
}
