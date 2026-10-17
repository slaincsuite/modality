package cn.slainc.policy.aspect;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.stp.StpLogic;
import cn.slainc.core.context.RequestContext;
import cn.slainc.core.context.TenantContext;
import cn.slainc.core.context.UserContext;
import cn.slainc.core.enums.LoginType;
import cn.slainc.policy.engine.PolicyEngine;
import cn.slainc.policy.domain.PolicyDecision;
import cn.slainc.policy.domain.PolicyRequest;
import cn.slainc.policy.spi.PolicyGroupProvider;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Aspect
public class PolicyAspect {

    private static final Pattern UNRESOLVED_PLACEHOLDER = Pattern.compile("\\{[^}]+}");

    private final PolicyEngine engine;

    private final ObjectProvider<PolicyGroupProvider> groupProvider;

    public PolicyAspect(PolicyEngine engine) {
        this(engine, null);
    }

    public PolicyAspect(PolicyEngine engine, ObjectProvider<PolicyGroupProvider> groupProvider) {
        this.engine = engine;
        this.groupProvider = groupProvider;
    }

    @Around("@annotation(requirePolicy)")
    public Object around(ProceedingJoinPoint joinPoint, RequirePolicy requirePolicy) throws Throwable {
        LoginType loginType = resolveLoginType(requirePolicy);
        StpLogic logic = SaManager.getStpLogic(loginType.value());
        logic.checkLogin();
        Object loginId = logic.getLoginIdDefaultNull();
        String resource = resolveResource(requirePolicy.resource(), joinPoint);
        List<String> roles = logic.getRoleList();
        PolicyGroupProvider groupsProvider = groupProvider == null ? null : groupProvider.getIfAvailable();
        List<String> groups = groupsProvider == null
                ? List.of() : groupsProvider.groups(loginId, loginType);
        PolicyRequest request = PolicyRequest.of(requirePolicy.action(), resource)
                .userId(String.valueOf(loginId))
                .loginType(loginType.value())
                .tenantId(TenantContext.getOrDefault())
                .roles(roles)
                .groups(groups);
        request.setEnv(environment(logic, request));
        PolicyDecision decision = engine.evaluate(request);
        if (!decision.allowed()) {
            throw new NotPermissionException(requirePolicy.action());
        }
        return joinPoint.proceed();
    }

    private Map<String, Object> environment(StpLogic logic, PolicyRequest request) {
        Map<String, Object> env = new HashMap<>();
        RequestContext.RequestInfo requestInfo = RequestContext.get();
        env.put(PolicyRequest.KEY_USER_ID, request.getUserId());
        env.put(PolicyRequest.KEY_TENANT_ID, request.getTenantId());
        env.put(PolicyRequest.KEY_LOGIN_TYPE, request.getLoginType());
        env.put(PolicyRequest.KEY_ROLES, request.getRoles());
        env.put(PolicyRequest.KEY_GROUPS, request.getGroups());
        env.put(PolicyRequest.KEY_SOURCE_IP, RequestContext.clientIp());
        env.put(PolicyRequest.KEY_CURRENT_TIME, Instant.now().toString());
        env.put(PolicyRequest.KEY_REQUEST_PATH, requestInfo == null ? null : requestInfo.requestUri());
        env.put(PolicyRequest.KEY_HTTP_METHOD, requestInfo == null ? null : requestInfo.method());
        env.put(PolicyRequest.KEY_USER_AGENT, RequestContext.userAgent());
        boolean safe = logic.isSafe("MFA") || logic.isSafe("DEFAULT");
        env.put(PolicyRequest.KEY_MFA_VERIFIED, safe);
        env.put(PolicyRequest.KEY_SAFE_AUTH, safe);
        return env;
    }

    private LoginType resolveLoginType(RequirePolicy requirePolicy) {
        if (requirePolicy.loginType() != null && !requirePolicy.loginType().isBlank()) {
            LoginType configured = LoginType.of(requirePolicy.loginType().trim());
            if (configured == null) {
                throw new IllegalArgumentException("不支持的账号体系: " + requirePolicy.loginType());
            }
            return configured;
        }
        LoginType current = UserContext.loginType();
        return current == null ? LoginType.TENANT_USER : current;
    }

    private String resolveResource(String template, ProceedingJoinPoint joinPoint) {
        if (!template.contains("{")) {
            return template;
        }
        String[] parameterNames = ((MethodSignature) joinPoint.getSignature()).getParameterNames();
        Object[] arguments = joinPoint.getArgs();
        String resolved = template;
        if (parameterNames != null) {
            for (int index = 0; index < parameterNames.length; index++) {
                String value = arguments[index] == null ? "null" : String.valueOf(arguments[index]);
                resolved = resolved.replace("{" + parameterNames[index] + "}", value);
            }
        }
        if (UNRESOLVED_PLACEHOLDER.matcher(resolved).find()) {
            throw new IllegalStateException("策略资源占位符无法解析: " + resolved);
        }
        return resolved;
    }
}
