package cn.slainc.policy.support;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;
import cn.slainc.policy.config.PolicyKeys;
import cn.slainc.policy.config.PolicyProperties;
import cn.slainc.policy.engine.PolicyEngine;
import cn.slainc.policy.event.PolicyChangedEvent;
import cn.slainc.policy.domain.Policy;
import cn.slainc.policy.domain.PolicyDocument;
import cn.slainc.policy.domain.PolicyStatus;
import cn.slainc.policy.domain.PolicySubjectType;
import cn.slainc.policy.storage.PolicyAuditRecord;
import cn.slainc.policy.storage.PolicyStore;
import cn.slainc.policy.storage.PolicyVersionRecord;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

public class PolicyAdminService {

    private final PolicyStore store;

    private final PolicyEngine engine;

    private final PolicyProperties properties;

    private final ObjectProvider<DynamicConfigService> configService;

    private final ApplicationEventPublisher eventPublisher;

    public PolicyAdminService(PolicyStore store, PolicyEngine engine, PolicyProperties properties,
                              ObjectProvider<DynamicConfigService> configService,
                              ApplicationEventPublisher eventPublisher) {
        this.store = store;
        this.engine = engine;
        this.properties = properties;
        this.configService = configService;
        this.eventPublisher = eventPublisher;
    }

    public Policy create(String name, PolicySubjectType subjectType, String subjectId, String loginType,
                         PolicyDocument document, String description, String operator) {
        Policy policy = Policy.create(name, subjectType, subjectId, loginType, document);
        policy.setDescription(description == null ? "" : description);
        policy.setCreator(operator);
        Policy saved = store.save(policy);
        afterChange(saved, "CREATE", null, saved.getDocument().toJson(), operator);
        return saved;
    }

    public Policy update(String id, PolicyDocument document, String operator) {
        Policy existing = require(id);
        String before = existing.getDocument().toJson();
        existing.setDocument(document);
        Policy saved = store.save(existing);
        afterChange(saved, "UPDATE", before, saved.getDocument().toJson(), operator);
        return saved;
    }

    public Policy changeStatus(String id, boolean enabled, String operator) {
        Policy existing = require(id);
        String before = existing.getDocument().toJson();
        existing.setStatus(enabled ? PolicyStatus.ENABLED : PolicyStatus.DISABLED);
        Policy saved = store.save(existing);
        afterChange(saved, enabled ? "ENABLE" : "DISABLE", before, saved.getDocument().toJson(), operator);
        return saved;
    }

    public Policy rollback(String id, int targetVersion, String operator) {
        Policy existing = require(id);
        PolicyVersionRecord record = store.findVersion(id, targetVersion)
                .orElseThrow(() -> new BizException(ResultCode.DATA_NOT_FOUND,
                        "策略 " + id + " 不存在版本 " + targetVersion + "，无法回滚"));
        String before = existing.getDocument().toJson();
        existing.setDocument(PolicyDocument.parse(record.getDocument()));
        Policy saved = store.save(existing);
        afterChange(saved, "ROLLBACK", before, saved.getDocument().toJson(), operator);
        return saved;
    }

    public void delete(String id, String operator) {
        Policy existing = require(id);
        store.delete(id);
        engine.invalidate();
        broadcast();
        if (eventPublisher != null) {
            eventPublisher.publishEvent(new PolicyChangedEvent(id, "DELETE", System.currentTimeMillis()));
        }
        PolicyAuditRecord audit = new PolicyAuditRecord();
        audit.setPolicyId(id);
        audit.setOperation("DELETE");
        audit.setOperator(operator);
        audit.setBeforeContent(existing.getDocument().toJson());
        audit.setCreatedAt(System.currentTimeMillis());
        store.appendAudit(audit);
    }

    public Policy importTemplate(String templateName, String name, PolicySubjectType subjectType, String subjectId,
                                 String operator) {
        return create(name, subjectType, subjectId, null, PolicyTemplates.template(templateName), 
                "从模板 " + templateName + " 导入", operator);
    }

    public Policy importJson(String json, String name, PolicySubjectType subjectType, String subjectId,
                             String operator) {
        return create(name, subjectType, subjectId, null, PolicyDocument.parse(json), null, operator);
    }

    public Policy get(String id) {
        return require(id);
    }

    public List<Policy> list() {
        return store.list();
    }

    public List<PolicyVersionRecord> versions(String id) {
        return store.listVersions(id);
    }

    public List<PolicyAuditRecord> audit(String id, int limit) {
        return store.listAudit(id, limit);
    }

    private Policy require(String id) {
        Optional<Policy> found = store.find(id);
        return found.orElseThrow(() -> new BizException(ResultCode.DATA_NOT_FOUND, "策略 " + id + " 不存在"));
    }

    private void afterChange(Policy saved, String operation, String before, String after, String operator) {
        store.archiveVersion(saved.getId(), saved.getVersion(), after, operator,
                properties.getJdbc().getRetainedVersions());
        PolicyAuditRecord audit = new PolicyAuditRecord();
        audit.setPolicyId(saved.getId());
        audit.setOperation(operation);
        audit.setOperator(operator);
        audit.setBeforeContent(before);
        audit.setAfterContent(after);
        audit.setCreatedAt(System.currentTimeMillis());
        store.appendAudit(audit);
        engine.invalidate();
        broadcast();
        if (eventPublisher != null) {
            eventPublisher.publishEvent(new PolicyChangedEvent(saved.getId(), operation, System.currentTimeMillis()));
        }
    }

    private void broadcast() {
        DynamicConfigService service = configService == null ? null : configService.getIfAvailable();
        if (service == null) {
            return;
        }
        service.set(PolicyKeys.REFRESH_CONFIG_KEY, String.valueOf(System.currentTimeMillis()),
                PolicyKeys.REFRESH_CONFIG_GROUP);
    }
}
