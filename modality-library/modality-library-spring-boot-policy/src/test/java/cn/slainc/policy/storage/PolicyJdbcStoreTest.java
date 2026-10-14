package cn.slainc.policy.storage;

import cn.slainc.core.exception.BizException;
import cn.slainc.policy.admin.PolicyAdminService;
import cn.slainc.policy.config.PolicyProperties;
import cn.slainc.policy.engine.PolicyEngine;
import cn.slainc.policy.domain.Effect;
import cn.slainc.policy.domain.Policy;
import cn.slainc.policy.domain.PolicyDocument;
import cn.slainc.policy.domain.PolicyRequest;
import cn.slainc.policy.domain.PolicyStatement;
import cn.slainc.policy.domain.PolicySubjectType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:policy-jdbc-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "modality.policy.store=jdbc"
        })
class PolicyJdbcStoreTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PolicyStore policyStore;

    @Autowired
    private PolicyAdminService adminService;

    @Autowired
    private PolicyEngine engine;

    @Autowired
    private ApplicationContext applicationContext;

    private PolicyDocument document(Effect effect, List<String> actions) {
        PolicyStatement statement = new PolicyStatement();
        statement.setSid("s1");
        statement.setEffect(effect);
        statement.setActions(actions);
        statement.setResources(List.of("*"));
        return PolicyDocument.of(statement);
    }

    @Test
    void storeIsJdbcAndTablesCreated() {
        assertInstanceOf(JdbcPolicyStore.class, policyStore);
        Long tables = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE UPPER(table_name) IN ('T_POLICY','T_POLICY_VERSION','T_POLICY_AUDIT')",
                Long.class);
        assertEquals(3L, tables);
    }

    @Test
    void saveAndReadViaDatabaseAdvancesVersion() {
        Policy created = adminService.create("jdbc策略", PolicySubjectType.GLOBAL, null, null,
                document(Effect.ALLOW, List.of("modality:user:*")), "jdbc 测试", "操作员甲");
        assertEquals(1, created.getVersion());
        Long rows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM t_policy WHERE id = ?", Long.class,
                created.getId());
        assertEquals(1L, rows);
        Policy updated = adminService.update(created.getId(),
                document(Effect.ALLOW, List.of("modality:user:*", "modality:order:*")), "操作员乙");
        assertEquals(2, updated.getVersion());
        assertEquals(2, adminService.versions(created.getId()).size());
        assertEquals("UPDATE", adminService.audit(created.getId(), 10).get(0).getOperation());
    }

    @Test
    void optimisticLockConflictBlockedAtDatabase() {
        Policy created = adminService.create("并发策略", PolicySubjectType.GLOBAL, null, null,
                document(Effect.ALLOW, List.of("modality:parallel:*")), null, "甲");
        Policy stale = policyStore.find(created.getId()).orElseThrow();
        adminService.update(created.getId(), document(Effect.ALLOW, List.of("modality:parallel:*", "x:y:z")), "乙");
        stale.setDocument(document(Effect.DENY, List.of("modality:parallel:*")));
        assertThrows(BizException.class, () -> policyStore.save(stale));
    }

    @Test
    void duplicateInsertIsTranslatedToChineseConflict() {
        Policy created = adminService.create("重复策略", PolicySubjectType.GLOBAL, null, null,
                document(Effect.ALLOW, List.of("modality:dup:*")), null, "甲");
        Policy duplicate = policyStore.find(created.getId()).orElseThrow();

        BizException failure = assertThrows(BizException.class,
                () -> ((JdbcPolicyStore) policyStore).insert(duplicate, duplicate.getDocument().toJson(),
                        System.currentTimeMillis()));

        assertTrue(failure.getMessage().contains("已被他人创建"));
    }

    @Test
    void rollbackReadsHistoricalDocument() {
        Policy created = adminService.create("回滚策略", PolicySubjectType.GLOBAL, null, null,
                document(Effect.DENY, List.of("modality:rollback:delete")), null, "甲");
        adminService.update(created.getId(), document(Effect.ALLOW, List.of("*")), "乙");
        Policy rolledBack = adminService.rollback(created.getId(), 1, "丙");
        assertTrue(rolledBack.getDocument().getStatements().get(0).getActions()
                .contains("modality:rollback:delete"));
        assertEquals(3, rolledBack.getVersion());
        assertNotNull(adminService.audit(created.getId(), 10));
    }

    @Test
    void engineEvaluatesFromDatabaseSnapshot() {
        Policy created = adminService.create("引擎策略", PolicySubjectType.USER, "99001", "TENANT_USER",
                document(Effect.ALLOW, List.of("modality:engine:*")), null, "甲");
        PolicyRequest request = PolicyRequest.of("modality:engine:get", "modality:engine/r")
                .userId("99001").loginType("TENANT_USER");
        assertTrue(engine.evaluate(request).allowed());
        adminService.changeStatus(created.getId(), false, "乙");
        assertFalse(engine.evaluate(request).allowed());
        adminService.delete(created.getId(), "丙");
        assertFalse(engine.evaluate(request).allowed());
    }

    @Test
    void retainedVersionCountFollowsConfig() {
        Policy created = adminService.create("保留策略", PolicySubjectType.GLOBAL, null, null,
                document(Effect.ALLOW, List.of("modality:keep:*")), null, "甲");
        for (int index = 0; index < 12; index++) {
            adminService.update(created.getId(),
                    document(Effect.ALLOW, List.of("modality:keep:*:" + index)), "操作员" + index);
        }
        PolicyProperties properties = applicationContext.getBean(PolicyProperties.class);
        List<PolicyVersionRecord> versions = adminService.versions(created.getId());
        assertEquals(properties.getJdbc().getRetainedVersions(), versions.size());
        assertTrue(versions.stream().noneMatch(record -> record.getVersion() <= 13 - properties.getJdbc().getRetainedVersions()));
    }
}
