package cn.slainc.sso.autoconfigure;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class ServerSecretKeyConfiguredCondition extends SpringBootCondition {

    public static final String SECRET_KEY_PROPERTY = "modality.sso.server.secret-key";

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String secret = context.getEnvironment().getProperty(SECRET_KEY_PROPERTY);
        if (secret == null || secret.isBlank()) {
            return ConditionOutcome.noMatch("未配置 " + SECRET_KEY_PROPERTY + "，已禁用 SSO 服务端装配");
        }
        return ConditionOutcome.match();
    }
}
