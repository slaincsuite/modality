package cn.slainc.idempotent.autoconfigure;

import cn.slainc.idempotent.store.IdempotentStore;
import cn.slainc.idempotent.token.IdempotentTokenService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = IdempotentAutoConfiguration.class)
@ConditionalOnProperty(prefix = IdempotentProperties.PREFIX + ".token", name = "enabled", matchIfMissing = true)
public class IdempotentTokenAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(IdempotentTokenService.class)
    public IdempotentTokenService idempotentTokenService(IdempotentStore idempotentStore,
                                                         IdempotentProperties properties) {
        return new IdempotentTokenService(idempotentStore, properties);
    }
}
