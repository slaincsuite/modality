package cn.slainc.modules.policy.generator;

import cn.slainc.core.exception.BizException;
import cn.slainc.modules.policy.dto.PolicyGeneratorRequest;
import cn.slainc.modules.policy.dto.PolicyGeneratorRule;
import cn.slainc.modules.policy.dto.PolicyGeneratorView;
import cn.slainc.modules.policy.service.catalog.PolicyCatalogService;
import cn.slainc.modules.policy.service.generator.PolicyGeneratorService;
import cn.slainc.policy.domain.PolicyDocument;
import cn.slainc.policy.domain.PolicyStatement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PolicyGeneratorServiceTest {

    private PolicyGeneratorService generator;

    @BeforeEach
    void setUp() {
        generator = new PolicyGeneratorService(null, new PolicyCatalogService());
    }

    @Test
    void previewNormalizesActionsResourcesAndAddsDangerousDenyGuardrail() {
        PolicyGeneratorView view = generator.preview(request("upms",
                new PolicyGeneratorRule(null, "allow", "upms",
                        List.of("user:query", "user:query", "user:update"),
                        List.of("user/*"), null)));

        PolicyDocument document = PolicyDocument.parse(view.document());
        PolicyStatement generated = document.getStatements().getFirst();
        PolicyStatement guardrail = document.getStatements().getLast();
        assertThat(generated.getActions()).containsExactly(
                "modality:upms:user:query", "modality:upms:user:update");
        assertThat(generated.getResources()).containsExactly("modality:upms:user/*");
        assertThat(generated.getSid()).isEqualTo("generated-1");
        assertThat(guardrail.getEffect()).isEqualTo("deny");
        assertThat(guardrail.getSid()).isEqualTo("guardrail-dangerous-actions");
        assertThat(guardrail.getActions()).contains("modality:*:reset-password");
        assertThat(view.allowCount()).isEqualTo(1);
        assertThat(view.denyCount()).isEqualTo(1);
    }

    @Test
    void previewNormalizesConditionsAndWarnsForCustomKeysAndServices() {
        PolicyGeneratorView view = generator.preview(request("custom-service",
                new PolicyGeneratorRule(" office-rule ", "allow", "custom-service",
                        List.of("document:read"), List.of("*"),
                        Map.of(" string_equal ",
                                Map.of(" modality:Department ", List.of(" finance ", "finance"))))));

        PolicyStatement statement = PolicyDocument.parse(view.document()).getStatements().getFirst();
        assertThat(statement.getSid()).isEqualTo("office-rule");
        assertThat(statement.getCondition().get("string_equal"))
                .containsEntry("modality:Department", List.of("finance"));
        assertThat(view.warnings()).anyMatch(warning -> warning.contains("自定义服务"))
                .anyMatch(warning -> warning.contains("不是内置请求属性"))
                .anyMatch(warning -> warning.contains("resource 为 *"));
    }

    @Test
    void previewCanDisableGuardrailButAlwaysWarns() {
        PolicyGeneratorView view = generator.preview(new PolicyGeneratorRequest(
                "只读策略", null, "global", null, null, null,
                List.of(new PolicyGeneratorRule("read", "allow", "upms",
                        List.of("user:query"), List.of("user/*"), null)), false));

        assertThat(PolicyDocument.parse(view.document()).getStatements()).hasSize(1);
        assertThat(view.warnings()).anyMatch(warning -> warning.contains("护栏已关闭"));
    }

    @Test
    void previewRejectsInvalidSubjectsValuesAndOversizedRules() {
        assertThatThrownBy(() -> generator.preview(new PolicyGeneratorRequest(
                "无效主体", null, "role", " ", null, null,
                List.of(rule("user:query")), true)))
                .isInstanceOf(BizException.class).hasMessageContaining("作用对象标识");
        assertThatThrownBy(() -> generator.preview(new PolicyGeneratorRequest(
                "非法主体", null, "unknown", "1", null, null,
                List.of(rule("user:query")), true)))
                .isInstanceOf(BizException.class).hasMessageContaining("作用对象");
        assertThatThrownBy(() -> generator.preview(new PolicyGeneratorRequest(
                "空白动作", null, "global", null, null, null,
                List.of(rule("user: query")), true)))
                .isInstanceOf(BizException.class).hasMessageContaining("空白或控制字符");
        assertThatThrownBy(() -> generator.preview(new PolicyGeneratorRequest(
                "超量规则", null, "global", null, null, null,
                java.util.stream.IntStream.range(0, 65)
                        .mapToObj(index -> new PolicyGeneratorRule("rule-" + index, "allow", "upms",
                                List.of("user:query"), List.of("user/*"), null)).toList(), true)))
                .isInstanceOf(BizException.class).hasMessageContaining("最多 64 条");
    }

    @Test
    void previewWarnsWhenDocumentHasNoAllowStatement() {
        PolicyGeneratorView view = generator.preview(request("upms",
                new PolicyGeneratorRule("deny-write", "deny", "upms",
                        List.of("user:update"), List.of("user/*"), null)));

        assertThat(view.allowCount()).isZero();
        assertThat(view.warnings()).anyMatch(warning -> warning.contains("没有 allow 语句"));
    }

    @Test
    void previewSupportsMixedAllowAndDenyStatements() {
        PolicyGeneratorView view = generator.preview(new PolicyGeneratorRequest(
                "混合权限", null, "global", null, null, null,
                List.of(
                        new PolicyGeneratorRule("allow-read", "allow", "upms",
                                List.of("user:query"), List.of("user/*"), null),
                        new PolicyGeneratorRule("deny-write", "deny", "upms",
                                List.of("user:update"), List.of("user/*"), null)),
                false));

        assertThat(view.statementCount()).isEqualTo(2);
        assertThat(view.allowCount()).isEqualTo(1);
        assertThat(view.denyCount()).isEqualTo(1);
    }

    private PolicyGeneratorRequest request(String name, PolicyGeneratorRule rule) {
        return new PolicyGeneratorRequest(name, null, "global", null, null, null,
                List.of(rule), true);
    }

    private PolicyGeneratorRule rule(String action) {
        return new PolicyGeneratorRule(null, "allow", "upms", List.of(action), List.of("user/*"), null);
    }
}
