package cn.slainc.policy.engine;

import cn.slainc.policy.FakeConfigService;
import cn.slainc.policy.SingleProvider;
import cn.slainc.policy.config.PolicyProperties;
import cn.slainc.policy.model.Effect;
import cn.slainc.policy.model.Policy;
import cn.slainc.policy.model.PolicyDecision;
import cn.slainc.policy.model.PolicyDocument;
import cn.slainc.policy.model.PolicyRequest;
import cn.slainc.policy.model.PolicyStatement;
import cn.slainc.policy.model.PolicySubjectType;
import cn.slainc.policy.storage.InMemoryPolicyStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyEngineTest {

    private InMemoryPolicyStore store;

    private FakeConfigService configService;

    private PolicyProperties properties;

    private PolicyEngine engine;

    @BeforeEach
    void setUp() {
        store = new InMemoryPolicyStore();
        configService = new FakeConfigService();
        properties = new PolicyProperties();
        properties.setSnapshotTtlSeconds(60);
        properties.getDecisionCache().setTtlSeconds(60);
        engine = new PolicyEngine(store, SingleProvider.of(configService), properties);
    }

    private Policy createPolicy(PolicySubjectType subjectType, String subjectId, Effect effect,
                                List<String> actions, List<String> resources,
                                Map<String, Map<String, List<String>>> condition) {
        PolicyStatement statement = new PolicyStatement();
        statement.setSid("s1");
        statement.setEffect(effect);
        statement.setActions(actions);
        statement.setResources(resources);
        statement.setCondition(condition);
        Policy policy = Policy.create("测试策略", subjectType, subjectId, null, PolicyDocument.of(statement));
        return store.save(policy);
    }

    @Test
    void noPolicyDefaultsToDeny() {
        PolicyDecision decision = engine.evaluate(PolicyRequest.of("modality:user:get", "modality:user/1"));
        assertEquals(PolicyDecision.Decision.NOT_APPLICABLE, decision.decision());
        assertFalse(decision.allowed());
        assertTrue(decision.reason().contains("默认拒绝"));
    }

    @Test
    void matchedAllowStatementGrantsAccess() {
        createPolicy(PolicySubjectType.GLOBAL, null, Effect.ALLOW, List.of("modality:user:*"), List.of("*"), null);
        PolicyDecision decision = engine.evaluate(PolicyRequest.of("modality:user:get", "modality:user/1"));
        assertEquals(PolicyDecision.Decision.ALLOW, decision.decision());
        assertEquals(1, decision.matches().size());
        assertEquals("modality:user:*", decision.matches().get(0).actionPattern());
    }

    @Test
    void denyTakesPrecedenceOverAllow() {
        createPolicy(PolicySubjectType.GLOBAL, null, Effect.ALLOW, List.of("modality:user:*"), List.of("*"), null);
        createPolicy(PolicySubjectType.GLOBAL, null, Effect.DENY, List.of("modality:user:delete"), List.of("*"), null);
        PolicyDecision deleteDecision = engine.evaluate(PolicyRequest.of("modality:user:delete", "modality:user/1"));
        assertEquals(PolicyDecision.Decision.DENY, deleteDecision.decision());
        PolicyDecision getDecision = engine.evaluate(PolicyRequest.of("modality:user:get", "modality:user/1"));
        assertEquals(PolicyDecision.Decision.ALLOW, getDecision.decision());
    }

    @Test
    void statementWithFailedConditionDoesNotApply() {
        createPolicy(PolicySubjectType.GLOBAL, null, Effect.ALLOW, List.of("modality:user:*"), List.of("*"),
                Map.of("ip_in_range", Map.of("modality:SourceIp", List.of("10.0.0.0/8"))));
        PolicyRequest inside = PolicyRequest.of("modality:user:get", "modality:user/1")
                .env("modality:SourceIp", "10.1.2.3");
        assertEquals(PolicyDecision.Decision.ALLOW, engine.evaluate(inside).decision());
        PolicyRequest outside = PolicyRequest.of("modality:user:get", "modality:user/1")
                .env("modality:SourceIp", "8.8.8.8");
        PolicyDecision decision = engine.evaluate(outside);
        assertEquals(PolicyDecision.Decision.NOT_APPLICABLE, decision.decision());
        assertEquals(1, decision.matches().size());
        assertFalse(decision.matches().get(0).matched());
        assertEquals(1, decision.matches().get(0).conditions().size());
    }

    @Test
    void multipleConditionsAreAnded() {
        createPolicy(PolicySubjectType.GLOBAL, null, Effect.ALLOW, List.of("modality:user:*"), List.of("*"),
                Map.of("string_equal", Map.of("modality:UserId", List.of("1001")),
                        "ip_in_range", Map.of("modality:SourceIp", List.of("10.0.0.0/8"))));
        PolicyRequest bothPass = PolicyRequest.of("modality:user:get", "modality:user/1")
                .env("modality:UserId", "1001").env("modality:SourceIp", "10.1.1.1");
        assertEquals(PolicyDecision.Decision.ALLOW, engine.evaluate(bothPass).decision());
        PolicyRequest oneFail = PolicyRequest.of("modality:user:get", "modality:user/1")
                .env("modality:UserId", "1002").env("modality:SourceIp", "10.1.1.1");
        assertEquals(PolicyDecision.Decision.NOT_APPLICABLE, engine.evaluate(oneFail).decision());
    }

    @Test
    void userAndTenantSubjectsFilterByIdentity() {
        createPolicy(PolicySubjectType.USER, "1001", Effect.ALLOW, List.of("modality:user:*"), List.of("*"), null);
        createPolicy(PolicySubjectType.TENANT, "2", Effect.ALLOW, List.of("modality:order:*"), List.of("*"), null);
        assertTrue(engine.evaluate(PolicyRequest.of("modality:user:get", "r").userId("1001")).allowed());
        assertFalse(engine.evaluate(PolicyRequest.of("modality:user:get", "r").userId("1002")).allowed());
        assertTrue(engine.evaluate(PolicyRequest.of("modality:order:get", "r").tenantId(2L)).allowed());
        assertFalse(engine.evaluate(PolicyRequest.of("modality:order:get", "r").tenantId(3L)).allowed());
    }

    @Test
    void roleSubjectFiltersByRoleList() {
        createPolicy(PolicySubjectType.ROLE, "admin", Effect.ALLOW, List.of("modality:*:*"), List.of("*"), null);
        assertTrue(engine.evaluate(PolicyRequest.of("modality:user:get", "r").roles(List.of("admin"))).allowed());
        assertFalse(engine.evaluate(PolicyRequest.of("modality:user:get", "r").roles(List.of("user"))).allowed());
    }

    @Test
    void loginTypeRestrictionBlocksOtherSystems() {
        PolicyStatement statement = new PolicyStatement();
        statement.setEffect(Effect.ALLOW);
        statement.setActions(List.of("modality:user:*"));
        statement.setResources(List.of("*"));
        Policy policy = Policy.create("仅用户体系", PolicySubjectType.GLOBAL, null, "TENANT_USER",
                PolicyDocument.of(statement));
        store.save(policy);
        assertTrue(engine.evaluate(PolicyRequest.of("modality:user:get", "r").loginType("TENANT_USER").userId("1")).allowed());
        assertFalse(engine.evaluate(PolicyRequest.of("modality:user:get", "r").loginType("PLATFORM_ADMIN").userId("1")).allowed());
    }

    @Test
    void disabledPolicySkipped() {
        Policy policy = createPolicy(PolicySubjectType.GLOBAL, null, Effect.ALLOW,
                List.of("modality:user:*"), List.of("*"), null);
        policy.setStatus("disabled");
        store.save(policy);
        assertEquals(PolicyDecision.Decision.NOT_APPLICABLE,
                engine.evaluate(PolicyRequest.of("modality:user:get", "r")).decision());
    }

    @Test
    void resourceWildcardAndPathStyleMatch() {
        createPolicy(PolicySubjectType.GLOBAL, null, Effect.ALLOW,
                List.of("modality:doc:*"), List.of("modality:tenant/2/*"), null);
        assertTrue(engine.evaluate(PolicyRequest.of("modality:doc:get", "modality:tenant/2/doc/9")).allowed());
        assertFalse(engine.evaluate(PolicyRequest.of("modality:doc:get", "modality:tenant/3/doc/9")).allowed());
    }

    @Test
    void missingActionOrResourceGivesChineseError() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> engine.evaluate(PolicyRequest.of(null, "r")));
        assertTrue(exception.getMessage().contains("action 与 resource"));
    }

    @Test
    void resolveActionsReturnsAllowPatternsNotCoveredByDeny() {
        createPolicy(PolicySubjectType.USER, "1001", Effect.ALLOW,
                List.of("modality:user:*", "modality:order:*"), List.of("*"), null);
        createPolicy(PolicySubjectType.USER, "1001", Effect.DENY, List.of("modality:user:delete"), List.of("*"), null);
        List<String> actions = engine.resolveActions(new PolicyRequest().userId("1001"));
        assertTrue(actions.contains("modality:order:*"));
        assertFalse(actions.contains("modality:user:*"));
        assertFalse(actions.contains("modality:user:delete"));
    }

    @Test
    void resolveActionsSkipsConditionalStatements() {
        createPolicy(PolicySubjectType.USER, "1001", Effect.ALLOW, List.of("modality:user:*"), List.of("*"),
                Map.of("ip_in_range", Map.of("modality:SourceIp", List.of("10.0.0.0/8"))));
        assertEquals(List.of(), engine.resolveActions(new PolicyRequest().userId("1001")));
    }

    @Test
    void decisionCachePreventsStoreReadsOnHit() {
        CountingStore countingStore = new CountingStore();
        PolicyEngine cachedEngine = new PolicyEngine(countingStore, SingleProvider.empty(), properties);
        Policy policy = Policy.create("缓存", PolicySubjectType.GLOBAL, null, null, PolicyDocument.of(
                statementOf(Effect.ALLOW, List.of("modality:user:*"))));
        countingStore.save(policy);
        PolicyRequest request = PolicyRequest.of("modality:user:get", "modality:user/1");
        cachedEngine.evaluate(request);
        int afterFirst = countingStore.listEnabledCalls.get();
        assertEquals(1, afterFirst);
        cachedEngine.evaluate(request);
        assertEquals(afterFirst, countingStore.listEnabledCalls.get());
        cachedEngine.invalidate();
        cachedEngine.evaluate(request);
        assertTrue(countingStore.listEnabledCalls.get() > afterFirst);
    }

    @Test
    void volatileCurrentTimeDoesNotChurnDecisionCache() throws Exception {
        CountingStore countingStore = new CountingStore();
        PolicyEngine cachedEngine = new PolicyEngine(countingStore, SingleProvider.empty(), properties);
        countingStore.save(Policy.create("缓存", PolicySubjectType.GLOBAL, null, null,
                PolicyDocument.of(statementOf(Effect.ALLOW, List.of("modality:user:*")))));
        java.lang.reflect.Field field = PolicyEngine.class.getDeclaredField("decisionCache");
        field.setAccessible(true);
        Map<?, ?> cache = (Map<?, ?>) field.get(cachedEngine);

        cachedEngine.evaluate(PolicyRequest.of("modality:user:get", "modality:user/1")
                .env(PolicyRequest.KEY_CURRENT_TIME, "2026-01-01T00:00:00.001Z"));
        int afterFirst = cache.size();
        cachedEngine.evaluate(PolicyRequest.of("modality:user:get", "modality:user/1")
                .env(PolicyRequest.KEY_CURRENT_TIME, "2026-01-01T00:00:00.999Z"));

        assertEquals(1, afterFirst);
        assertEquals(afterFirst, cache.size(), "时变字段不得参与决策缓存指纹");
    }

    @Test
    void policiesUsingCurrentTimeSkipDecisionCache() throws Exception {
        CountingStore countingStore = new CountingStore();
        PolicyEngine cachedEngine = new PolicyEngine(countingStore, SingleProvider.empty(), properties);
        countingStore.save(Policy.create("按时段", PolicySubjectType.GLOBAL, null, null, PolicyDocument.of(
                statementOf(Effect.ALLOW, List.of("modality:user:*"), Map.of("date_less_than",
                        Map.of(PolicyRequest.KEY_CURRENT_TIME, List.of("2099-01-01T00:00:00Z")))))));
        java.lang.reflect.Field field = PolicyEngine.class.getDeclaredField("decisionCache");
        field.setAccessible(true);
        Map<?, ?> cache = (Map<?, ?>) field.get(cachedEngine);

        assertTrue(cachedEngine.evaluate(PolicyRequest.of("modality:user:get", "modality:user/1")
                .env(PolicyRequest.KEY_CURRENT_TIME, "2026-01-01T00:00:00Z")).allowed());

        assertEquals(0, cache.size(), "条件引用时变维度时必须放弃缓存以保证判定正确");
    }

    @Test
    void configVersionChangeTriggersSnapshotRefresh() {
        createPolicy(PolicySubjectType.GLOBAL, null, Effect.ALLOW, List.of("modality:user:*"), List.of("*"), null);
        assertTrue(engine.evaluate(PolicyRequest.of("modality:user:get", "r")).allowed());
        configService.put("modality.policy.refresh", "1");
        createPolicy(PolicySubjectType.GLOBAL, null, Effect.DENY, List.of("modality:user:get"), List.of("*"), null);
        assertEquals(PolicyDecision.Decision.DENY, engine.evaluate(PolicyRequest.of("modality:user:get", "r")).decision());
    }

    @Test
    void hasResourceAccessSemantics() {
        createPolicy(PolicySubjectType.GLOBAL, null, Effect.ALLOW, List.of("modality:user:get"), List.of("*"), null);
        assertTrue(engine.hasResourceAccess(PolicyRequest.of("modality:user:get", "r")));
        assertFalse(engine.hasResourceAccess(PolicyRequest.of("modality:user:delete", "r")));
    }

    @Test
    void hasRolePoliciesFollowsSnapshot() {
        assertFalse(engine.hasRolePolicies());
        createPolicy(PolicySubjectType.ROLE, "admin", Effect.ALLOW, List.of("*"), List.of("*"), null);
        engine.invalidate();
        assertTrue(engine.hasRolePolicies());
    }

    private PolicyStatement statementOf(Effect effect, List<String> actions) {
        return statementOf(effect, actions, null);
    }

    private PolicyStatement statementOf(Effect effect, List<String> actions,
                                        Map<String, Map<String, List<String>>> condition) {
        PolicyStatement statement = new PolicyStatement();
        statement.setEffect(effect);
        statement.setActions(actions);
        statement.setResources(List.of("*"));
        statement.setCondition(condition);
        return statement;
    }

    private static class CountingStore extends InMemoryPolicyStore {

        final AtomicInteger listEnabledCalls = new AtomicInteger();

        @Override
        public List<Policy> listEnabled() {
            listEnabledCalls.incrementAndGet();
            return super.listEnabled();
        }
    }
}
