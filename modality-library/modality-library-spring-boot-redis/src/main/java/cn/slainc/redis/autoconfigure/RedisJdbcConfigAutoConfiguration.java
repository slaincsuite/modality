package cn.slainc.redis.autoconfigure;

import cn.slainc.redis.config.DynamicConfigRepository;
import cn.slainc.redis.config.JdbcDynamicConfigRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

@AutoConfiguration
@AutoConfigureBefore(name = "cn.slainc.redis.autoconfigure.RedisConfigAutoConfiguration")
@AutoConfigureAfter(name = "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration")
@ConditionalOnClass(name = "org.springframework.jdbc.core.JdbcTemplate")
@ConditionalOnBean(JdbcTemplate.class)
@ConditionalOnProperty(prefix = "modality.redis.config", name = {"enabled", "jdbc-repository-enabled"},
        matchIfMissing = true)
public class RedisJdbcConfigAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DynamicConfigRepository jdbcDynamicConfigRepository(JdbcTemplate jdbcTemplate,
                                                              RedisProperties properties) {
        JdbcDynamicConfigRepository repository = new JdbcDynamicConfigRepository(jdbcTemplate,
                properties.getConfig().getTable());
        repository.initSchema();
        return repository;
    }
}
