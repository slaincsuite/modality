package cn.slainc.idempotent.autoconfigure;

import cn.slainc.idempotent.store.DatabaseIdempotentStore;
import cn.slainc.idempotent.store.IdempotentStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@AutoConfiguration(afterName = "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration")
@ConditionalOnClass(name = "org.springframework.jdbc.core.JdbcTemplate")
@ConditionalOnBean(type = "org.springframework.jdbc.core.JdbcTemplate")
@ConditionalOnProperty(prefix = IdempotentProperties.PREFIX + ".store", name = "type", havingValue = "database")
public class IdempotentDatabaseStoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(IdempotentStore.class)
    public DatabaseIdempotentStore databaseIdempotentStore(JdbcTemplate jdbcTemplate, IdempotentProperties properties,
                                                           ObjectProvider<PlatformTransactionManager> transactionManager) {
        return new DatabaseIdempotentStore(jdbcTemplate, properties.getStore().getTableName(),
                properties.getStore().isAutoCreateTable(), transactionManager.getIfAvailable());
    }
}
