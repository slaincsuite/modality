package cn.slainc.policy.aspect;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.stp.StpLogic;
import cn.slainc.core.context.RequestContext;
import cn.slainc.core.context.TenantContext;
import cn.slainc.policy.engine.PolicyEngine;
import cn.slainc.policy.model.PolicyDecision;
import cn.slainc.policy.model.PolicyRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Aspect
public class PolicyAspect {

    private final PolicyEngine engine;

    public PolicyAspect(PolicyEngine engine) {
        this.engine = engine;
    }

    @Around("@annotation(requirePolicy)")
    public Object around(ProceedingJoinPoint joinPoint, RequirePolicy requirePolicy) throws Throwable {
        StpLogic logic = SaManager.getStpLogic(requirePolicy.loginType());
        logic.checkLogin();
        String resource = resolveResource(requirePolicy.resource(), joinPoint);
        PolicyRequest request = PolicyRequest.of(requirePolicy.action(), resource)
                .userId(String.valueOf(logic.getLoginIdDefaultNull()))
                .loginType(requirePolicy.loginType())
                .tenantId(TenantContext.get())
                .roles(engine.hasRolePolicies() ? logic.getRoleList() : List.of());
        Map<String, Object> env = new HashMap<>();
        env.put(PolicyRequest.KEY_USER_ID, request.getUserId());
        env.put(PolicyRequest.KEY_TENANT_ID, TenantContext.get());
        env.put(PolicyRequest.KEY_SOURCE_IP, RequestContext.clientIp());
        env.put(PolicyRequest.KEY_CURRENT_TIME, Instant.now().toString());
        request.setEnv(env);
        PolicyDecision decision = engine.evaluate(request);
        if (!decision.allowed()) {
            throw new NotPermissionException(requirePolicy.action());
        }
        return joinPoint.proceed();
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
        return resolved;
    }
}
