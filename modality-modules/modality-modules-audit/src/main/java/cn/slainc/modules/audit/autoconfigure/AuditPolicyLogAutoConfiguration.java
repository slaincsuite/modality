package cn.slainc.modules.audit.autoconfigure;

import cn.slainc.modules.audit.config.AuditConfigReader;
import cn.slainc.modules.audit.aspect.PolicyEvaluationAuditAspect;
import cn.slainc.modules.audit.service.PolicyEvaluationLogService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;


// 策略评估审计切面：policy 库在类路径时环绕 PolicyEngine.evaluate 记录 deny 决策，缺依赖时整个配置静默退避
@AutoConfiguration
@ConditionalOnClass(name = "cn.slainc.policy.engine.PolicyEngine")
@ConditionalOnProperty(prefix = "modality.audit.policy-log", name = "enabled", matchIfMissing = true)
public class AuditPolicyLogAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PolicyEvaluationAuditAspect policyEvaluationAuditAspect(PolicyEvaluationLogService logService,
                                                                   AuditConfigReader configReader) {
        return new PolicyEvaluationAuditAspect(logService, configReader);
    }
}
