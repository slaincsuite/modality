package cn.slainc.modules.upms.service;

import cn.slainc.core.enums.LoginType;
import cn.slainc.core.exception.BizException;
import cn.slainc.modules.upms.UpmsIntegrationTestBase;
import cn.slainc.modules.upms.entity.UserRecord;
import cn.slainc.policy.support.PolicyAdminService;
import cn.slainc.policy.support.PolicyBindingService;
import cn.slainc.policy.domain.PolicyBinding;
import cn.slainc.policy.engine.PolicyEngine;
import cn.slainc.policy.domain.Effect;
import cn.slainc.policy.domain.PolicyDocument;
import cn.slainc.policy.domain.PolicyRequest;
import cn.slainc.policy.domain.PolicyStatement;
import cn.slainc.policy.domain.PolicySubjectType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PolicyBindingServiceTest extends UpmsIntegrationTestBase {

    @Autowired
    private PolicyBindingService bindingService;

    @Autowired
    private PolicyAdminService policyAdminService;

    @Autowired
    private PolicyEngine policyEngine;

    private String seedPolicy() {
        PolicyStatement statement = new PolicyStatement();
        statement.setSid("allow-report");
        statement.setEffect(Effect.ALLOW.name());
        statement.setActions(List.of("report:*"));
        statement.setResources(List.of("finance:quarterly"));
        PolicyDocument document = new PolicyDocument();
        document.setStatements(List.of(statement));
        return policyAdminService.create("报表查看", PolicySubjectType.ROLE, "nobody", null, document,
                "测试种子策略", "tester").getId();
    }

    @Test
    void bindToUserDrivesEngineDecision() {
        String policyId = seedPolicy();
        UserRecord user = userService.create("policy-user", null, "Policy#12345", null, null, null,
                LoginType.TENANT_USER, 1L, "admin");
        PolicyBinding binding = bindingService.bindToUser(policyId, user.getId(), LoginType.TENANT_USER,
                "tester");
        assertThat(binding.getSubjectType()).isEqualTo(PolicyBinding.SUBJECT_USER);
        assertThat(binding.getSubjectId()).isEqualTo(String.valueOf(user.getId()));

        policyEngine.invalidate();
        PolicyRequest request = PolicyRequest.of("report:read", "finance:quarterly")
                .userId(String.valueOf(user.getId())).loginType(LoginType.TENANT_USER.value());
        assertThat(policyEngine.evaluate(request).allowed()).isTrue();

        bindingService.unbind(binding.getId());
        policyEngine.invalidate();
        assertThat(policyEngine.evaluate(request).allowed()).isFalse();
        assertThat(bindingService.query(PolicyBinding.SUBJECT_USER, String.valueOf(user.getId()), null))
                .isEmpty();
    }

    @Test
    void bindToRoleMatchesRoleRequests() {
        String policyId = seedPolicy();
        RoleService roleService = applicationContext.getBean(RoleService.class);
        roleService.create("analyst", "分析员", null, null, null, null, null);
        PolicyBinding binding = bindingService.bindToRole(policyId, "analyst", "tester");
        assertThat(binding.getSubjectType()).isEqualTo(PolicyBinding.SUBJECT_ROLE);
        policyEngine.invalidate();
        PolicyRequest request = PolicyRequest.of("report:export", "finance:quarterly").roles(List.of("analyst"));
        assertThat(policyEngine.evaluate(request).allowed()).isTrue();
    }

    @Test
    void bindToClientMapsToAppLoginType() {
        String policyId = seedPolicy();
        PolicyBinding binding = bindingService.bindToClient(policyId, 3001L, "tester");
        assertThat(binding.getSubjectType()).isEqualTo(PolicyBinding.SUBJECT_CLIENT);
        assertThat(binding.getSubjectId()).isEqualTo("3001");
        policyEngine.invalidate();
        PolicyRequest request = PolicyRequest.of("report:read", "finance:quarterly")
                .userId("3001").loginType(LoginType.APP.value());
        assertThat(policyEngine.evaluate(request).allowed()).isTrue();
        // 未挂 APP 体系的其它主体不受影响
        PolicyRequest stranger = PolicyRequest.of("report:read", "finance:quarterly")
                .userId("9999").loginType(LoginType.APP.value());
        assertThat(policyEngine.evaluate(stranger).allowed()).isFalse();
    }

    @Test
    void bindRequiresExistingPolicyAndUnbindUnknownRejected() {
        assertThatThrownBy(() -> bindingService.bindToUser("not-exist", 1L, LoginType.TENANT_USER, "tester"))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> bindingService.unbind(404L)).isInstanceOf(BizException.class)
                .hasMessageContaining("不存在");
    }

    @Autowired
    private UserService userService;

    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;
}
