package cn.slainc.idempotent.autoconfigure;

import cn.slainc.idempotent.store.IdempotentStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = {IdempotentRedisStoreAutoConfiguration.class,
        IdempotentDatabaseStoreAutoConfiguration.class, IdempotentLocalStoreAutoConfiguration.class})
@ConditionalOnMissingBean(IdempotentStore.class)
public class IdempotentMissingStoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(IdempotentStore.class)
    public IdempotentStore missingIdempotentStore(IdempotentProperties properties) {
        throw new IllegalStateException("幂等存储未就绪：modality.idempotent.store.type="
                + properties.getStore().getType() + "（" + properties.getStore().getType().description()
                + "），请补齐对应依赖，或改为 local / 关闭 modality.idempotent.enabled");
    }
}
