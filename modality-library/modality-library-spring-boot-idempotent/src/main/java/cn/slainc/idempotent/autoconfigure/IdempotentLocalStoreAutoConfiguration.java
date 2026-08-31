package cn.slainc.idempotent.autoconfigure;

import cn.slainc.idempotent.store.IdempotentStore;
import cn.slainc.idempotent.store.LocalIdempotentStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = {IdempotentRedisStoreAutoConfiguration.class,
        IdempotentDatabaseStoreAutoConfiguration.class})
@ConditionalOnProperty(prefix = IdempotentProperties.PREFIX + ".store", name = "type", havingValue = "local",
        matchIfMissing = true)
public class IdempotentLocalStoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(IdempotentStore.class)
    public LocalIdempotentStore localIdempotentStore() {
        return new LocalIdempotentStore();
    }
}
