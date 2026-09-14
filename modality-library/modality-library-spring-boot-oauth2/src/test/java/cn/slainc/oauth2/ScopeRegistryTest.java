package cn.slainc.oauth2;

import cn.slainc.oauth2.scope.ScopeDefinition;
import cn.slainc.oauth2.scope.ScopeRegistry;
import cn.slainc.oauth2.scope.ScopeRiskLevel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScopeRegistryTest {

    private ScopeRegistry registry(List<ScopeDefinition> defaults, FakeConfigService config) {
        return new ScopeRegistry(defaults, new SingleProvider<>(config));
    }

    @Test
    void partitionsHigherAndLowerScopesByRiskLevel() {
        ScopeDefinition low = definition("userinfo", ScopeRiskLevel.LOW);
        ScopeDefinition high = definition("pay", ScopeRiskLevel.HIGH);
        ScopeRegistry scopeRegistry = registry(List.of(low, high), new FakeConfigService());

        assertThat(scopeRegistry.higherScopeNames()).containsExactly("pay");
        assertThat(scopeRegistry.lowerScopeNames()).containsExactly("userinfo");
        assertThat(scopeRegistry.find("pay")).isPresent();
        assertThat(scopeRegistry.find("missing")).isEmpty();
    }

    @Test
    void dynamicConfigOverridesDefaults() {
        FakeConfigService config = new FakeConfigService();
        ScopeRegistry scopeRegistry = registry(List.of(definition("pay", ScopeRiskLevel.HIGH)), config);

        config.put("modality.oauth2.scopes",
                "[{\"name\":\"pay\",\"description\":\"支付\",\"group\":\"资金\",\"riskLevel\":\"LOW\"}]");
        assertThat(scopeRegistry.higherScopeNames()).isEmpty();
        assertThat(scopeRegistry.lowerScopeNames()).containsExactly("pay");
    }

    private ScopeDefinition definition(String name, ScopeRiskLevel riskLevel) {
        ScopeDefinition definition = new ScopeDefinition();
        definition.setName(name);
        definition.setDescription(name + " 描述");
        definition.setRiskLevel(riskLevel);
        return definition;
    }
}
