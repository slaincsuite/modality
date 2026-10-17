package cn.slainc.modules.policy.service.manage;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.PageResult;
import cn.slainc.core.result.ResultCode;
import cn.slainc.modules.policy.PolicyCenterKeys;
import cn.slainc.modules.policy.dto.PolicyBoundaryView;
import cn.slainc.modules.policy.dto.PolicyCreateRequest;
import cn.slainc.modules.policy.dto.PolicyCustomTemplateRequest;
import cn.slainc.modules.policy.dto.PolicyCustomTemplateView;
import cn.slainc.modules.policy.dto.PolicyParseView;
import cn.slainc.modules.policy.dto.PolicyView;
import cn.slainc.modules.policy.entity.PolicyMeta;
import cn.slainc.modules.policy.entity.PolicyBoundaryRecord;
import cn.slainc.modules.policy.entity.PolicyCustomTemplateRecord;
import cn.slainc.modules.policy.store.PolicyBoundaryStore;
import cn.slainc.modules.policy.store.PolicyMetaStore;
import cn.slainc.modules.policy.store.PolicyTemplateStore;
import cn.slainc.policy.support.PolicyAdminService;
import cn.slainc.policy.support.PolicyBindingService;
import cn.slainc.policy.support.PolicyDiffService;
import cn.slainc.policy.support.PolicyTemplates;
import cn.slainc.policy.domain.Policy;
import cn.slainc.policy.domain.PolicyDocument;
import cn.slainc.policy.domain.PolicySubjectType;
import cn.slainc.policy.storage.PolicyAuditRecord;
import cn.slainc.policy.storage.PolicyVersionRecord;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class PolicyManageService {

    private static final Map<String, String> SYSTEM_TEMPLATE_NAMES = new LinkedHashMap<>();

    static {
        SYSTEM_TEMPLATE_NAMES.put(PolicyTemplates.READ_ONLY, "系统只读策略");
        SYSTEM_TEMPLATE_NAMES.put(PolicyTemplates.FULL_ACCESS, "系统完全访问策略");
        SYSTEM_TEMPLATE_NAMES.put(PolicyTemplates.DENY_DANGEROUS, "系统拒绝危险操作策略");
        SYSTEM_TEMPLATE_NAMES.put(PolicyTemplates.INTERNAL_ONLY, "系统仅内网访问策略");
    }

    private final PolicyAdminService adminService;

    private final PolicyMetaStore metaStore;

    private final PolicyDiffService diffService;

    private final PolicyTemplateStore templateStore;

    private final PolicyBoundaryStore boundaryStore;

    public PolicyManageService(PolicyAdminService adminService, PolicyMetaStore metaStore,
                               PolicyDiffService diffService, PolicyTemplateStore templateStore,
                               PolicyBoundaryStore boundaryStore) {
        this.adminService = adminService;
        this.metaStore = metaStore;
        this.diffService = diffService;
        this.templateStore = templateStore;
        this.boundaryStore = boundaryStore;
    }

    public PolicyView create(PolicyCreateRequest request, String operator) {
        PolicyDocument document = PolicyDocument.parse(request.documentJson());
        Policy policy = adminService.create(request.name(), parseSubjectType(request.subjectType()),
                request.subjectId(), request.loginType(), document, request.description(), operator);
        metaStore.upsert(policy.getId(), request.group(), false);
        return view(policy.getId());
    }

    public PolicyView updateDocument(String id, String documentJson, String operator) {
        requireModifiable(id);
        adminService.update(id, PolicyDocument.parse(documentJson), operator);
        return view(id);
    }

    public PolicyView changeStatus(String id, boolean enabled, String operator) {
        requireModifiable(id);
        adminService.changeStatus(id, enabled, operator);
        return view(id);
    }

    public PolicyView changeGroup(String id, String group) {
        adminService.get(id);
        metaStore.updateGroup(id, group);
        return view(id);
    }

    public void delete(String id, String operator) {
        requireModifiable(id);
        adminService.delete(id, operator);
        metaStore.delete(id);
        boundaryStore.delete(id);
    }

    public PolicyView copy(String id, String name, String operator) {
        Policy source = adminService.get(id);
        String targetName = name == null || name.isBlank() ? source.getName() + "-副本" : name.trim();
        Policy created = adminService.create(targetName, source.getSubjectType(), source.getSubjectId(),
                source.getLoginType(), source.getDocument(), "复制自策略 " + source.getId(), operator);
        PolicyMeta meta = metaStore.find(id).orElse(null);
        metaStore.upsert(created.getId(), meta == null ? null : meta.group(), false);
        return view(created.getId());
    }

    public PolicyView importFromTemplate(String template, String name, String subjectType, String subjectId,
                                         String group, String operator) {
        Optional<PolicyCustomTemplateRecord> custom = findCustomTemplate(template);
        Policy policy = custom
                .map(record -> adminService.create(name, parseSubjectType(subjectType), subjectId, null,
                        PolicyDocument.parse(record.document()), "从自定义模板 " + record.name() + " 导入", operator))
                .orElseGet(() -> adminService.importTemplate(template, name, parseSubjectType(subjectType),
                        subjectId, operator));
        metaStore.upsert(policy.getId(), group, false);
        return view(policy.getId());
    }

    public List<PolicyView> seedSystemPolicies(String operator) {
        Map<String, PolicyMeta> metas = metaStore.listAll();
        List<PolicyView> seeded = new ArrayList<>();
        for (Map.Entry<String, String> entry : SYSTEM_TEMPLATE_NAMES.entrySet()) {
            boolean exists = adminService.list().stream()
                    .anyMatch(policy -> entry.getValue().equals(policy.getName())
                            && metas.getOrDefault(policy.getId(), new PolicyMeta(policy.getId(), "", false)).system());
            if (exists) {
                continue;
            }
            Policy policy = adminService.importTemplate(entry.getKey(), entry.getValue(),
                    PolicySubjectType.GLOBAL, null, operator);
            metaStore.upsert(policy.getId(), PolicyCenterKeys.DEFAULT_GROUP, true);
            seeded.add(view(policy.getId()));
        }
        return seeded;
    }

    public PolicyView view(String id) {
        Policy policy = adminService.get(id);
        return PolicyView.of(policy, metaStore.find(id).orElse(null));
    }

    public PageResult<PolicyView> page(String keyword, String subjectType, String subjectId, String status,
                                       String group, String creator, long current, long size) {
        return page(keyword, subjectType, subjectId, status, group, creator, null, current, size);
    }

    public PageResult<PolicyView> page(String keyword, String subjectType, String subjectId, String status,
                                       String group, String creator, Boolean system, long current, long size) {
        Map<String, PolicyMeta> metas = metaStore.listAll();
        List<PolicyView> matched = adminService.list().stream()
                .filter(policy -> keyword == null || keyword.isBlank()
                        || contains(policy.getName(), keyword)
                        || contains(policy.getDescription(), keyword)
                        || contains(policy.getDocument().toJson(), keyword))
                .filter(policy -> subjectType == null || subjectType.isBlank()
                        || policy.getSubjectType().code().equalsIgnoreCase(subjectType.trim()))
                .filter(policy -> subjectId == null || subjectId.isBlank()
                        || subjectId.trim().equals(policy.getSubjectId()))
                .filter(policy -> status == null || status.isBlank()
                        || policy.getStatus().code().equalsIgnoreCase(status.trim()))
                .filter(policy -> group == null || group.isBlank()
                        || metaGroup(metas, policy).equalsIgnoreCase(group.trim()))
                .filter(policy -> creator == null || creator.isBlank()
                        || creator.trim().equals(policy.getCreator()))
                .filter(policy -> !isBindingDerived(policy))
                .filter(policy -> system == null
                        || system == metas.getOrDefault(policy.getId(),
                        new PolicyMeta(policy.getId(), PolicyCenterKeys.DEFAULT_GROUP, false)).system())
                .sorted(Comparator.comparingLong(Policy::getUpdatedAt).reversed())
                .map(policy -> PolicyView.of(policy, metas.get(policy.getId())))
                .toList();
        return PageResult.from(matched, current, size);
    }

    public Map<String, Object> templates() {
        Map<String, Object> result = new LinkedHashMap<>();
        PolicyTemplates.presets().forEach((key, document) -> result.put(key, document.toJson()));
        templateStore.list().forEach(template -> result.put("custom:" + template.id(), template.document()));
        return result;
    }

    public PolicyParseView parse(String documentJson) {
        PolicyDocument document = PolicyDocument.parse(documentJson);
        return new PolicyParseView(true, document.getStatements().size(), document.toJson());
    }

    public List<PolicyCustomTemplateView> customTemplates() {
        return templateStore.list().stream().map(this::customTemplateView).toList();
    }

    public PolicyCustomTemplateView createCustomTemplate(PolicyCustomTemplateRequest request, String operator) {
        PolicyDocument document = PolicyDocument.parse(request.documentJson());
        String name = requireTemplateName(request.name());
        String description = normalizeDescription(request.description());
        return customTemplateView(templateStore.insert(name, description, document.toJson(), operator));
    }

    public PolicyCustomTemplateView updateCustomTemplate(String id, PolicyCustomTemplateRequest request) {
        PolicyDocument document = PolicyDocument.parse(request.documentJson());
        String name = requireTemplateName(request.name());
        String description = normalizeDescription(request.description());
        return customTemplateView(templateStore.update(id, name, description, document.toJson()));
    }

    public void deleteCustomTemplate(String id) {
        templateStore.delete(id);
    }

    public PolicyBoundaryView boundary(String policyId) {
        adminService.get(policyId);
        return boundaryStore.find(policyId).map(this::boundaryView)
                .orElseGet(() -> new PolicyBoundaryView(policyId, null, null, 0, 0));
    }

    public PolicyBoundaryView updateBoundary(String policyId, String documentJson, String operator) {
        adminService.get(policyId);
        PolicyDocument document = PolicyDocument.parse(documentJson);
        boundaryStore.save(policyId, document.toJson(), operator);
        return boundaryStore.find(policyId).map(this::boundaryView)
                .orElseThrow(() -> new BizException(ResultCode.DATA_STATE_INVALID, "策略边界保存后读取失败"));
    }

    public List<PolicyVersionRecord> versions(String id) {
        return adminService.versions(id);
    }

    public List<String> diff(String id, int fromVersion, int toVersion) {
        PolicyDocument before = versionDocument(id, fromVersion);
        PolicyDocument after = versionDocument(id, toVersion);
        return diffService.diff(before, after);
    }

    public PolicyView rollback(String id, int targetVersion, String operator) {
        requireModifiable(id);
        adminService.rollback(id, targetVersion, operator);
        return view(id);
    }

    public List<PolicyAuditRecord> audit(String id, int limit) {
        return adminService.audit(id, Math.max(limit, 1));
    }

    private PolicyDocument versionDocument(String id, int version) {
        Optional<PolicyVersionRecord> record = adminService.versions(id).stream()
                .filter(item -> item.getVersion() == version)
                .findFirst();
        return PolicyDocument.parse(record.orElseThrow(() -> new BizException(ResultCode.DATA_NOT_FOUND,
                "策略 " + id + " 不存在版本 " + version)).getDocument());
    }

    private String metaGroup(Map<String, PolicyMeta> metas, Policy policy) {
        PolicyMeta meta = metas.get(policy.getId());
        return meta == null ? PolicyCenterKeys.DEFAULT_GROUP : meta.group();
    }

    private boolean isBindingDerived(Policy policy) {
        return policy.getDescription() != null
                && policy.getDescription().startsWith(PolicyBindingService.DERIVED_DESCRIPTION_PREFIX);
    }

    private void requireModifiable(String id) {
        PolicyMeta meta = metaStore.find(id).orElse(null);
        if (meta != null && meta.system()) {
            throw new BizException(ResultCode.FORBIDDEN, "系统预置策略不允许修改或删除，可复制后自行编辑");
        }
    }

    private boolean contains(String text, String keyword) {
        return text != null && text.contains(keyword.trim());
    }

    private PolicySubjectType parseSubjectType(String subjectType) {
        if (subjectType == null || subjectType.isBlank()) {
            throw new BizException(ResultCode.PARAM_MISSING, "策略作用对象不能为空");
        }
        return PolicySubjectType.of(subjectType.trim());
    }

    private Optional<PolicyCustomTemplateRecord> findCustomTemplate(String template) {
        if (template == null || template.isBlank()) {
            return Optional.empty();
        }
        String key = template.startsWith("custom:") ? template.substring("custom:".length()) : template;
        if (!key.startsWith("tpl_")) {
            return Optional.empty();
        }
        return templateStore.find(key);
    }

    private PolicyCustomTemplateView customTemplateView(PolicyCustomTemplateRecord record) {
        return new PolicyCustomTemplateView(record.id(), "custom:" + record.id(), record.name(),
                record.description(), record.document(), record.creator(), record.createTime(), record.updateTime());
    }

    private PolicyBoundaryView boundaryView(PolicyBoundaryRecord record) {
        return new PolicyBoundaryView(record.policyId(), record.document(), record.operator(),
                record.createTime(), record.updateTime());
    }

    private String requireTemplateName(String name) {
        if (name == null || name.isBlank()) {
            throw new BizException(ResultCode.PARAM_MISSING, "自定义模板名称不能为空");
        }
        return name.trim();
    }

    private String normalizeDescription(String description) {
        return description == null ? "" : description.trim();
    }
}
