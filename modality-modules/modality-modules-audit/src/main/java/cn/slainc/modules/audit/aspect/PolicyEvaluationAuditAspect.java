package cn.slainc.modules.audit.aspect;

import cn.slainc.modules.audit.service.policylog.PolicyEvaluationLogService;

import cn.slainc.core.context.RequestContext;
import cn.slainc.core.context.TenantContext;
import cn.slainc.modules.audit.config.AuditConfigReader;
import cn.slainc.modules.audit.entity.PolicyEvaluationLogRecord;
import cn.slainc.policy.engine.PolicyEngine;
import cn.slainc.policy.domain.PolicyDecision;
import cn.slainc.policy.domain.PolicyRequest;
import cn.slainc.utils.json.JsonUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


// 环绕 policy 库 PolicyEngine.evaluate 的评估审计切面：deny 必记（含命中语句与条件明细），allow 可选记录
@Aspect
public class PolicyEvaluationAuditAspect {

    private static final Logger log = LoggerFactory.getLogger(PolicyEvaluationAuditAspect.class);

    private static final int STATEMENTS_MAX_LENGTH = 4000;

    private final PolicyEvaluationLogService logService;

    private final AuditConfigReader configReader;

    public PolicyEvaluationAuditAspect(PolicyEvaluationLogService logService,
                                       AuditConfigReader configReader) {
        this.logService = logService;
        this.configReader = configReader;
    }

    @Around("execution(* cn.slainc.policy.engine.PolicyEngine.evaluate(..))")
    public Object aroundEvaluate(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();
        try {
            if (!(result instanceof PolicyDecision decision)) {
                return result;
            }
            boolean denied = decision.decision() == PolicyDecision.Decision.DENY;
            if (!denied && !configReader.policyRecordAllow()) {
                return result;
            }
            if (decision.decision() == PolicyDecision.Decision.NOT_APPLICABLE) {
                return result;
            }
            PolicyEvaluationLogRecord record = new PolicyEvaluationLogRecord();
            if (joinPoint.getArgs().length > 0 && joinPoint.getArgs()[0] instanceof PolicyRequest request) {
                record.setLoginId(request.getUserId());
                record.setLoginType(request.getLoginType());
                record.setTenantId(request.getTenantId() == null ? TenantContext.get() : request.getTenantId());
                record.setAction(request.getAction());
                record.setResource(request.getResource());
            }
            record.setDecision(decision.decision().name());
            record.setMatchedStatements(truncate(JsonUtils.toJson(decision.matches()), STATEMENTS_MAX_LENGTH));
            record.setReason(decision.reason());
            record.setClientIp(RequestContext.clientIp());
            record.setRequestPath(currentPath());
            record.setCreateTime(System.currentTimeMillis());
            logService.insert(record);
        } catch (Exception e) {
            // 评估审计是旁路，任何失败都不影响策略判定本身
            log.warn("策略评估日志记录失败: {} {}", e.getClass().getName(), e.getMessage());
        }
        return result;
    }

    private String currentPath() {
        try {
            org.springframework.web.context.request.RequestAttributes attributes =
                    org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            if (attributes instanceof org.springframework.web.context.request.ServletRequestAttributes servletAttributes) {
                return servletAttributes.getRequest().getRequestURI();
            }
        } catch (Exception e) {
            log.debug("读取当前请求路径失败：{}", e.getMessage());
        }
        return null;
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }
}
