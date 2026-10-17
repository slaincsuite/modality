package cn.slainc.policy.support;

import cn.slainc.policy.SingleProvider;
import cn.slainc.policy.config.PolicyProperties;
import cn.slainc.policy.domain.Effect;
import cn.slainc.policy.domain.Policy;
import cn.slainc.policy.domain.PolicyBinding;
import cn.slainc.policy.domain.PolicyDocument;
import cn.slainc.policy.domain.PolicyStatement;
import cn.slainc.policy.domain.PolicySubjectType;
import cn.slainc.policy.engine.PolicyEngine;
import cn.slainc.policy.storage.InMemoryPolicyBindingStore;
import cn.slainc.policy.storage.InMemoryPolicyStore;
import cn.slainc.policy.storage.PolicyBindingOrigin;
import cn.slainc.policy.storage.PolicyBindingOriginStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PolicyBindingServiceTest {

    private InMemoryPolicyStore policyStore;

    private PolicyAdminService adminService;

    private PolicyBindingService bindingService;

    private FakeOriginStore originStore;

    @BeforeEach
    void setUp() {
        policyStore = new InMemoryPolicyStore();
        PolicyProperties properties = new PolicyProperties();
        PolicyEngine engine = new PolicyEngine(policyStore, SingleProvider.empty(), properties);
        adminService = new PolicyAdminService(policyStore, engine, properties, SingleProvider.empty(), null);
        originStore = new FakeOriginStore();
        bindingService = new PolicyBindingService(new InMemoryPolicyBindingStore(), adminService,
                SingleProvider.of(originStore));
    }

    @Test
    void groupBindingIsIdempotentAndTracksSourcePolicy() {
        Policy source = adminService.create("源策略", PolicySubjectType.GLOBAL, null, null, document(), null, "tester");
        PolicyBinding first = bindingService.bindToGroup(source.getId(), "team-a", "tester");
        PolicyBinding second = bindingService.bindToGroup(source.getId(), "team-a", "tester");

        assertEquals(first.getId(), second.getId());
        assertEquals(source.getId(), second.getSourcePolicyId());
        assertEquals("GROUP", second.getSubjectType());
        assertEquals(2, policyStore.list().size());
        assertEquals(1, bindingService.queryBySource(source.getId()).size());
    }

    private PolicyDocument document() {
        PolicyStatement statement = new PolicyStatement();
        statement.setEffect(Effect.ALLOW);
        statement.setActions(List.of("modality:test:read"));
        statement.setResources(List.of("*"));
        return PolicyDocument.of(statement);
    }

    private static class FakeOriginStore implements PolicyBindingOriginStore {

        private final Map<Long, PolicyBindingOrigin> origins = new LinkedHashMap<>();

        @Override
        public Optional<PolicyBindingOrigin> find(String sourcePolicyId, String subjectType, String subjectId,
                                                  Long tenantId) {
            return origins.values().stream().filter(origin -> origin.sourcePolicyId().equals(sourcePolicyId)
                    && origin.subjectType().equals(subjectType) && origin.subjectId().equals(subjectId)
                    && origin.tenantId().equals(tenantId)).findFirst();
        }

        @Override
        public List<PolicyBindingOrigin> listBySource(String sourcePolicyId, Long tenantId) {
            return origins.values().stream().filter(origin -> origin.sourcePolicyId().equals(sourcePolicyId)
                    && origin.tenantId().equals(tenantId)).toList();
        }

        @Override
        public void save(PolicyBindingOrigin origin) {
            origins.put(origin.bindingId(), origin);
        }

        @Override
        public void deleteByBindingId(Long bindingId) {
            origins.remove(bindingId);
        }
    }
}
