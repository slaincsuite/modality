package cn.slainc.policy.support;

import cn.slainc.core.exception.BizException;
import cn.slainc.policy.FakeConfigService;
import cn.slainc.policy.SingleProvider;
import cn.slainc.policy.config.PolicyProperties;
import cn.slainc.policy.engine.PolicyEngine;
import cn.slainc.policy.event.PolicyChangedEvent;
import cn.slainc.policy.domain.Effect;
import cn.slainc.policy.domain.Policy;
import cn.slainc.policy.domain.PolicyDocument;
import cn.slainc.policy.domain.PolicyRequest;
import cn.slainc.policy.domain.PolicyStatement;
import cn.slainc.policy.domain.PolicySubjectType;
import cn.slainc.policy.storage.InMemoryPolicyStore;
import cn.slainc.policy.storage.PolicyAuditRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyAdminServiceTest {

    private InMemoryPolicyStore store;

    private PolicyEngine engine;

    private FakeConfigService configService;

    private PolicyAdminService adminService;

    private final List<PolicyChangedEvent> events = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        store = new InMemoryPolicyStore();
        configService = new FakeConfigService();
        PolicyProperties properties = new PolicyProperties();
        engine = new PolicyEngine(store, SingleProvider.of(configService), properties);
        adminService = new PolicyAdminService(store, engine, properties, SingleProvider.of(configService),
                event -> {
                    if (event instanceof PolicyChangedEvent changedEvent) {
                        events.add(changedEvent);
                    }
                });
    }

    private PolicyDocument document(Effect effect, List<String> actions) {
        PolicyStatement statement = new PolicyStatement();
        statement.setSid("s1");
        statement.setEffect(effect);
        statement.setActions(actions);
        statement.setResources(List.of("*"));
        return PolicyDocument.of(statement);
    }

    @Test
    void createPersistsVersionAuditAndBroadcasts() {
        Policy created = adminService.create("只读", PolicySubjectType.GLOBAL, null, null,
                document(Effect.ALLOW, List.of("modality:*:get")), "说明", "操作员甲");
        assertEquals(1, created.getVersion());
        assertEquals(1, store.listVersions(created.getId()).size());
        List<PolicyAuditRecord> audits = store.listAudit(created.getId(), 10);
        assertEquals(1, audits.size());
        assertEquals("CREATE", audits.get(0).getOperation());
        assertEquals("操作员甲", audits.get(0).getOperator());
        assertEquals(1, events.size());
        assertEquals(created.getId(), events.get(0).policyId());
        assertNotNull(configService.getString("modality.policy.refresh", null));
    }

    @Test
    void updateAdvancesVersionAndKeepsHistory() {
        Policy created = adminService.create("策略", PolicySubjectType.USER, "1001", null,
                document(Effect.ALLOW, List.of("modality:user:*")), null, "甲");
        Policy updated = adminService.update(created.getId(),
                document(Effect.ALLOW, List.of("modality:user:*", "modality:order:*")), "乙");
        assertEquals(2, updated.getVersion());
        assertEquals(2, store.listVersions(created.getId()).size());
        assertEquals("UPDATE", store.listAudit(created.getId(), 10).get(0).getOperation());
    }

    @Test
    void optimisticLockConflictGivesChineseError() {
        Policy created = adminService.create("策略", PolicySubjectType.GLOBAL, null, null,
                document(Effect.ALLOW, List.of("*")), null, "甲");
        Policy stale = store.find(created.getId()).orElseThrow();
        adminService.update(created.getId(), document(Effect.ALLOW, List.of("modality:a:*")), "乙");
        stale.setDocument(document(Effect.ALLOW, List.of("modality:b:*")));
        BizException exception = assertThrows(BizException.class, () -> store.save(stale));
        assertTrue(exception.getMessage().contains("已被他人修改"));
    }

    @Test
    void rollbackRestoresHistoricalDocument() {
        Policy created = adminService.create("策略", PolicySubjectType.GLOBAL, null, null,
                document(Effect.DENY, List.of("modality:user:delete")), null, "甲");
        adminService.update(created.getId(), document(Effect.ALLOW, List.of("*")), "乙");
        Policy rolledBack = adminService.rollback(created.getId(), 1, "丙");
        assertEquals(3, rolledBack.getVersion());
        assertTrue(rolledBack.getDocument().getStatements().get(0).getActions().contains("modality:user:delete"));
        assertEquals("ROLLBACK", store.listAudit(created.getId(), 10).get(0).getOperation());
    }

    @Test
    void rollbackMissingVersionGivesChineseError() {
        Policy created = adminService.create("策略", PolicySubjectType.GLOBAL, null, null,
                document(Effect.ALLOW, List.of("*")), null, "甲");
        BizException exception = assertThrows(BizException.class,
                () -> adminService.rollback(created.getId(), 99, "甲"));
        assertTrue(exception.getMessage().contains("不存在版本 99"));
    }

    @Test
    void enableDisableReflectsInEngineEvaluation() {
        Policy created = adminService.create("策略", PolicySubjectType.GLOBAL, null, null,
                document(Effect.ALLOW, List.of("modality:user:*")), null, "甲");
        PolicyRequest request = PolicyRequest.of("modality:user:get", "modality:user/1");
        assertTrue(engine.evaluate(request).allowed());
        adminService.changeStatus(created.getId(), false, "乙");
        assertFalse(engine.evaluate(request).allowed());
        adminService.changeStatus(created.getId(), true, "乙");
        assertTrue(engine.evaluate(request).allowed());
    }

    @Test
    void deleteFallsBackToDefaultDeny() {
        Policy created = adminService.create("策略", PolicySubjectType.GLOBAL, null, null,
                document(Effect.ALLOW, List.of("*")), null, "甲");
        adminService.delete(created.getId(), "乙");
        assertFalse(engine.evaluate(PolicyRequest.of("modality:user:get", "r")).allowed());
        assertEquals("DELETE", store.listAudit(created.getId(), 10).get(0).getOperation());
        assertThrows(BizException.class, () -> adminService.get(created.getId()));
    }

    @Test
    void diffOutputsChineseChangeDetails() {
        PolicyDiffService diffService = new PolicyDiffService();
        PolicyStatement beforeStatement = new PolicyStatement();
        beforeStatement.setSid("s1");
        beforeStatement.setEffect(Effect.ALLOW);
        beforeStatement.setActions(List.of("modality:user:*"));
        beforeStatement.setResources(List.of("*"));
        PolicyStatement afterStatement = new PolicyStatement();
        afterStatement.setSid("s1");
        afterStatement.setEffect(Effect.DENY);
        afterStatement.setActions(List.of("modality:user:*"));
        afterStatement.setResources(List.of("*"));
        PolicyStatement addedStatement = new PolicyStatement();
        addedStatement.setSid("s2");
        addedStatement.setEffect(Effect.ALLOW);
        addedStatement.setActions(List.of("modality:order:*"));
        addedStatement.setResources(List.of("*"));

        PolicyDocument before = PolicyDocument.of(beforeStatement);
        PolicyDocument after = PolicyDocument.of(afterStatement, addedStatement);
        List<String> lines = diffService.diff(before, after);
        assertTrue(lines.stream().anyMatch(line -> line.contains("s1") && line.contains("效果")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("新增语句 s2")));
        assertEquals("两个策略文档内容一致",
                diffService.diff(PolicyDocument.of(beforeStatement), PolicyDocument.of(beforeStatement)).get(0));
    }

    @Test
    void templateImportAndInvalidTemplateName() {
        Policy imported = adminService.importTemplate(PolicyTemplates.READ_ONLY, "只读策略",
                PolicySubjectType.GLOBAL, null, "甲");
        assertTrue(engine.evaluate(PolicyRequest.of("modality:user:get", "r")).allowed());
        assertFalse(engine.evaluate(PolicyRequest.of("modality:user:delete", "r")).allowed());
        BizException exception = assertThrows(BizException.class,
                () -> adminService.importTemplate("not-exist", "x", PolicySubjectType.GLOBAL, null, "甲"));
        assertTrue(exception.getMessage().contains("策略模板 not-exist 不存在"));

        String json = "{\"version\":\"2.0\",\"statements\":[{\"effect\":\"deny\",\"actions\":[\"modality:*:purge\"],\"resources\":[\"*\"]}]}";
        Policy fromJson = adminService.importJson(json, "导入策略", PolicySubjectType.GLOBAL, null, "甲");
        assertEquals(PolicySubjectType.GLOBAL, fromJson.getSubjectType());
    }

    @Test
    void allPresetTemplatesValid() {
        PolicyTemplates.presets().forEach((name, document) -> document.validate());
        assertEquals(20, PolicyTemplates.presets().size());
        assertTrue(PolicyTemplates.presets().containsKey(PolicyTemplates.BREAK_GLASS));
        assertTrue(PolicyTemplates.presets().containsKey(PolicyTemplates.MFA_EXPORT_GUARD));
        assertTrue(PolicyTemplates.presets().containsKey(PolicyTemplates.TENANT_SELF_SERVICE));
        Map<String, Map<String, List<String>>> condition = PolicyTemplates.template(PolicyTemplates.INTERNAL_ONLY)
                .getStatements().get(0).getCondition();
        assertNotNull(condition.get("ip_in_range"));
    }
}
