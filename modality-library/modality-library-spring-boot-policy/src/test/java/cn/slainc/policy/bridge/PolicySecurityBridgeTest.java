package cn.slainc.policy.bridge;

import cn.slainc.core.enums.LoginType;
import cn.slainc.policy.FakeConfigService;
import cn.slainc.policy.SingleProvider;
import cn.slainc.policy.config.PolicyProperties;
import cn.slainc.policy.engine.PolicyEngine;
import cn.slainc.policy.domain.Effect;
import cn.slainc.policy.domain.Policy;
import cn.slainc.policy.domain.PolicyDocument;
import cn.slainc.policy.domain.PolicyStatement;
import cn.slainc.policy.domain.PolicySubjectType;
import cn.slainc.policy.storage.InMemoryPolicyStore;
import cn.slainc.policy.spi.PolicyGroupProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicySecurityBridgeTest {

    private InMemoryPolicyStore store;

    private PolicySecurityBridge bridge;

    @BeforeEach
    void setUp() {
        store = new InMemoryPolicyStore();
        PolicyEngine engine = new PolicyEngine(store, SingleProvider.of(new FakeConfigService()), new PolicyProperties());
        bridge = new PolicySecurityBridge(engine);
    }

    private void savePolicy(PolicySubjectType subjectType, String subjectId, String loginType, Effect effect,
                            List<String> actions) {
        PolicyStatement statement = new PolicyStatement();
        statement.setEffect(effect);
        statement.setActions(actions);
        statement.setResources(List.of("*"));
        store.save(Policy.create("桥测试", subjectType, subjectId, loginType, PolicyDocument.of(statement)));
    }

    @Test
    void permissionResolutionReturnsAllowPatterns() {
        savePolicy(PolicySubjectType.USER, "1001", "TENANT_USER", Effect.ALLOW, List.of("modality:user:*"));
        savePolicy(PolicySubjectType.USER, "1002", "TENANT_USER", Effect.ALLOW, List.of("modality:order:*"));
        assertEquals(List.of("modality:user:*", "user:*"), bridge.getPermissions("1001", LoginType.TENANT_USER));
        assertEquals(List.of("modality:order:*", "order:*"), bridge.getPermissions("1002", LoginType.TENANT_USER));
        assertEquals(List.of(), bridge.getPermissions("1003", LoginType.TENANT_USER));
    }

    @Test
    void denyPatternIsExportedForPermissionMatcher() {
        savePolicy(PolicySubjectType.USER, "1001", "TENANT_USER", Effect.ALLOW, List.of("modality:user:*"));
        savePolicy(PolicySubjectType.USER, "1001", "TENANT_USER", Effect.DENY, List.of("modality:user:delete"));
        List<String> permissions = bridge.getPermissions("1001", LoginType.TENANT_USER);
        assertEquals(List.of("modality:user:*", "user:*",
                "!modality:user:delete", "!user:delete"), permissions);
    }

    @Test
    void emptyPolicyYieldsEmptyListsAndRolesAlwaysEmpty() {
        assertEquals(List.of(), bridge.getPermissions("1001", LoginType.TENANT_USER));
        assertEquals(List.of(), bridge.getRoles("1001", LoginType.TENANT_USER));
    }

    @Test
    void groupProviderParticipatesInPermissionResolution() {
        PolicyGroupProvider provider = (loginId, type) -> List.of("team-a");
        PolicyEngine engine = new PolicyEngine(store, SingleProvider.of(new FakeConfigService()), new PolicyProperties());
        PolicySecurityBridge groupBridge = new PolicySecurityBridge(engine, SingleProvider.of(provider));
        savePolicy(PolicySubjectType.GROUP, "team-a", null, Effect.ALLOW, List.of("modality:team:*"));
        assertEquals(List.of("modality:team:*", "team:*"),
                groupBridge.getPermissions("1001", LoginType.TENANT_USER));
    }
}
