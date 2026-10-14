package cn.slainc.oauth2.protocol;

import cn.slainc.oauth2.scope.ScopeDefinition;
import cn.slainc.oauth2.scope.ScopeRegistry;

import java.util.ArrayList;
import java.util.List;

public record ConfirmView(String clientId, List<ScopeView> scopes, String message) {

    public static ConfirmView of(String clientId, List<String> requestedScopes, ScopeRegistry scopeRegistry) {
        List<ScopeView> views = new ArrayList<>();
        if (requestedScopes != null) {
            for (String scope : requestedScopes) {
                ScopeDefinition definition = scopeRegistry.find(scope).orElse(null);
                if (definition == null) {
                    views.add(new ScopeView(scope, "未登记的自定义权限", "自定义", "未分级"));
                } else {
                    views.add(new ScopeView(definition.getName(), definition.getDescription(),
                            definition.getGroup(), definition.getRiskLevel().label()));
                }
            }
        }
        return new ConfirmView(clientId, views, "本次操作需要您确认授权");
    }

    public record ScopeView(String name, String description, String group, String riskLevel) {
    }
}
