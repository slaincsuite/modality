package cn.slainc.oauth2.scope;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.oauth2.config.Oauth2Keys;
import cn.slainc.utils.json.JsonUtils;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class ScopeRegistry {

    private final List<ScopeDefinition> defaultScopes;

    private final ObjectProvider<DynamicConfigService> configService;

    public ScopeRegistry(List<ScopeDefinition> defaultScopes, ObjectProvider<DynamicConfigService> configService) {
        this.defaultScopes = defaultScopes == null ? List.of() : defaultScopes;
        this.configService = configService;
    }

    public List<ScopeDefinition> list() {
        DynamicConfigService service = configService.getIfAvailable();
        if (service != null) {
            String json = service.getString(Oauth2Keys.SCOPE_CONFIG_KEY, "");
            if (!json.isBlank()) {
                List<ScopeDefinition> overridden = JsonUtils.parseList(json, ScopeDefinition.class);
                if (overridden != null && !overridden.isEmpty()) {
                    return overridden;
                }
            }
        }
        return defaultScopes;
    }

    public Optional<ScopeDefinition> find(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return list().stream().filter(s -> name.equals(s.getName())).findFirst();
    }

    public List<String> higherScopeNames() {
        Set<String> names = new LinkedHashSet<>();
        for (ScopeDefinition definition : list()) {
            if (definition.getRiskLevel() == ScopeRiskLevel.HIGH || definition.requiresConfirm()) {
                names.add(definition.getName());
            }
        }
        return new ArrayList<>(names);
    }

    public List<String> lowerScopeNames() {
        Set<String> names = new LinkedHashSet<>();
        for (ScopeDefinition definition : list()) {
            if (!definition.requiresConfirm()) {
                names.add(definition.getName());
            }
        }
        return new ArrayList<>(names);
    }
}
