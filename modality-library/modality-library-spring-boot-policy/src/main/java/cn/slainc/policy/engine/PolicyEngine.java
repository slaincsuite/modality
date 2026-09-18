package cn.slainc.policy.engine;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.policy.config.PolicyKeys;
import cn.slainc.policy.config.PolicyProperties;
import cn.slainc.policy.match.WildcardMatcher;
import cn.slainc.policy.model.ConditionEvaluation;
import cn.slainc.policy.model.ConditionOperator;
import cn.slainc.policy.model.Effect;
import cn.slainc.policy.model.Policy;
import cn.slainc.policy.model.PolicyDecision;
import cn.slainc.policy.model.PolicyRequest;
import cn.slainc.policy.model.PolicySubjectType;
import cn.slainc.policy.model.StatementMatch;
import cn.slainc.policy.operator.ConditionEvaluators;
import cn.slainc.policy.storage.PolicyStore;
import cn.slainc.utils.json.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

public class PolicyEngine {

    private static final Logger log = LoggerFactory.getLogger(PolicyEngine.class);

    private final PolicyStore store;

    private final ObjectProvider<DynamicConfigService> configService;

    private final PolicyProperties properties;

    private final Map<String, CachedDecision> decisionCache = new ConcurrentHashMap<>();

    private volatile Snapshot snapshot;

    public PolicyEngine(PolicyStore store, ObjectProvider<DynamicConfigService> configService,
                        PolicyProperties properties) {
        this.store = store;
        this.configService = configService;
        this.properties = properties;
    }

    public PolicyDecision evaluate(PolicyRequest request) {
        if (request.getAction() == null || request.getAction().isBlank()
                || request.getResource() == null || request.getResource().isBlank()) {
            throw new IllegalArgumentException("策略评估必须提供 action 与 resource");
        }
        Snapshot current = snapshot();
        String cacheKey = null;
        PolicyProperties.DecisionCache cacheSetting = properties.getDecisionCache();
        if (cacheSetting.isEnabled()) {
            cacheKey = current.version() + "|" + fingerprint(request);
            CachedDecision cached = decisionCache.get(cacheKey);
            if (cached != null && !cached.expired(System.currentTimeMillis())) {
                return cached.decision();
            }
        }
        PolicyDecision decision = doEvaluate(current, request);
        if (cacheKey != null) {
            if (decisionCache.size() >= cacheSetting.getMaxSize()) {
                decisionCache.clear();
            }
            decisionCache.put(cacheKey, new CachedDecision(decision,
                    System.currentTimeMillis() + cacheSetting.getTtlSeconds() * 1000));
        }
        return decision;
    }

    public boolean hasResourceAccess(PolicyRequest request) {
        return evaluate(request).allowed();
    }

    public List<String> resolveActions(PolicyRequest request) {
        Snapshot current = snapshot();
        LinkedHashSet<String> allowed = new LinkedHashSet<>();
        LinkedHashSet<String> denied = new LinkedHashSet<>();
        for (Policy policy : current.policies()) {
            if (!policy.enabled() || !subjectApplies(policy, request)) {
                continue;
            }
            for (var statement : policy.getDocument().getStatements()) {
                if (statement.hasCondition()) {
                    continue;
                }
                if (statement.effectEnum() == Effect.ALLOW) {
                    allowed.addAll(statement.getActions());
                } else {
                    denied.addAll(statement.getActions());
                }
            }
        }
        allowed.removeIf(pattern -> denied.stream()
                .anyMatch(deny -> WildcardMatcher.match(deny, pattern) || WildcardMatcher.match(pattern, deny)));
        return List.copyOf(allowed);
    }

    public List<Policy> snapshotPolicies() {
        return snapshot().policies();
    }

    public boolean hasRolePolicies() {
        return snapshot().policies().stream()
                .anyMatch(policy -> policy.getSubjectType() == PolicySubjectType.ROLE);
    }

    public void invalidate() {
        snapshot = null;
        decisionCache.clear();
    }

    private PolicyDecision doEvaluate(Snapshot current, PolicyRequest request) {
        List<StatementMatch> matched = new ArrayList<>();
        for (Policy policy : current.policies()) {
            if (!policy.enabled() || !subjectApplies(policy, request)) {
                continue;
            }
            for (var statement : policy.getDocument().getStatements()) {
                String actionPattern = WildcardMatcher.firstMatch(statement.getActions(), request.getAction());
                if (actionPattern == null) {
                    continue;
                }
                String resourcePattern = WildcardMatcher.firstMatch(statement.getResources(), request.getResource());
                if (resourcePattern == null) {
                    continue;
                }
                List<ConditionEvaluation> evaluations = evaluateConditions(statement.getCondition(), request.getEnv());
                boolean passed = evaluations.stream().allMatch(ConditionEvaluation::passed);
                matched.add(new StatementMatch(policy.getId(), policy.getName(), statement.getSid(),
                        statement.effectEnum(), actionPattern, resourcePattern, evaluations, passed));
            }
        }
        List<StatementMatch> applied = matched.stream().filter(StatementMatch::matched).toList();
        return applied.stream().filter(match -> match.effect() == Effect.DENY).findFirst()
                .map(match -> PolicyDecision.deny(matched, "命中拒绝语句 " + describe(match)))
                .orElseGet(() -> applied.stream().filter(match -> match.effect() == Effect.ALLOW).findFirst()
                        .map(match -> PolicyDecision.allow(matched, "命中允许语句 " + describe(match)))
                        .orElseGet(() -> new PolicyDecision(PolicyDecision.Decision.NOT_APPLICABLE,
                                List.copyOf(matched), "没有策略允许该操作，默认拒绝")));
    }

    private List<ConditionEvaluation> evaluateConditions(Map<String, Map<String, List<String>>> condition,
                                                         Map<String, Object> env) {
        if (condition == null || condition.isEmpty()) {
            return List.of();
        }
        List<ConditionEvaluation> evaluations = new ArrayList<>();
        for (var operatorEntry : condition.entrySet()) {
            ConditionOperator operator = ConditionOperator.of(operatorEntry.getKey());
            for (var keyEntry : operatorEntry.getValue().entrySet()) {
                Object actual = env == null ? null : env.get(keyEntry.getKey());
                boolean passed = ConditionEvaluators.evaluate(operator, actual, keyEntry.getValue());
                evaluations.add(new ConditionEvaluation(operator.code(), keyEntry.getKey(), actual,
                        keyEntry.getValue(), passed));
            }
        }
        return evaluations;
    }

    private boolean subjectApplies(Policy policy, PolicyRequest request) {
        if (policy.getLoginType() != null
                && (request.getLoginType() == null || !policy.getLoginType().equalsIgnoreCase(request.getLoginType()))) {
            return false;
        }
        if (policy.getSubjectType() == PolicySubjectType.GLOBAL) {
            return true;
        }
        if (policy.getSubjectType() == PolicySubjectType.TENANT) {
            return request.getTenantId() != null
                    && policy.getSubjectId().equals(String.valueOf(request.getTenantId()));
        }
        if (policy.getSubjectType() == PolicySubjectType.ROLE) {
            return request.getRoles() != null && request.getRoles().contains(policy.getSubjectId());
        }
        return request.getUserId() != null && policy.getSubjectId().equals(request.getUserId());
    }

    private Snapshot snapshot() {
        String version = currentVersion();
        long now = System.currentTimeMillis();
        Snapshot current = snapshot;
        if (current != null && current.version().equals(version)
                && now - current.loadedAt() < properties.getSnapshotTtlSeconds() * 1000) {
            return current;
        }
        synchronized (this) {
            current = snapshot;
            if (current != null && current.version().equals(version)
                    && System.currentTimeMillis() - current.loadedAt() < properties.getSnapshotTtlSeconds() * 1000) {
                return current;
            }
            List<Policy> loaded;
            try {
                loaded = store.listEnabled();
            } catch (RuntimeException exception) {
                if (current != null) {
                    log.warn("刷新策略快照失败，继续沿用旧快照: {}", exception.getMessage());
                    return current;
                }
                throw exception;
            }
            Snapshot fresh = new Snapshot(version, List.copyOf(loaded), System.currentTimeMillis());
            snapshot = fresh;
            decisionCache.clear();
            return fresh;
        }
    }

    private String currentVersion() {
        DynamicConfigService service = configService == null ? null : configService.getIfAvailable();
        if (service == null) {
            return "0";
        }
        return service.getString(PolicyKeys.REFRESH_CONFIG_KEY, "0");
    }

    private String fingerprint(PolicyRequest request) {
        Map<String, Object> ordered = new TreeMap<>();
        if (request.getEnv() != null) {
            ordered.putAll(request.getEnv());
        }
        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("userId", request.getUserId());
        identity.put("loginType", request.getLoginType());
        identity.put("tenantId", request.getTenantId());
        identity.put("roles", request.getRoles() == null ? List.of() : request.getRoles().stream().sorted().toList());
        identity.put("action", request.getAction());
        identity.put("resource", request.getResource());
        identity.put("env", ordered);
        return JsonUtils.toJson(identity);
    }

    private String describe(StatementMatch match) {
        return match.policyId() + "/" + (match.sid() == null ? match.actionPattern() : match.sid());
    }

    private record CachedDecision(PolicyDecision decision, long expireAt) {

        boolean expired(long now) {
            return now >= expireAt;
        }
    }

    record Snapshot(String version, List<Policy> policies, long loadedAt) {
    }
}
