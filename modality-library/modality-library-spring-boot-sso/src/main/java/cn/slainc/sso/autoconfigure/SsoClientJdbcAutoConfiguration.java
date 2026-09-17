package cn.slainc.sso.autoconfigure;

import cn.slainc.sso.client.JdbcSsoClientRecordRepository;
import cn.slainc.sso.client.SsoClientRecordRepository;
import cn.slainc.sso.config.SsoProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

@AutoConfiguration(afterName = "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration")
@ConditionalOnClass(name = "org.springframework.jdbc.core.JdbcTemplate")
@ConditionalOnBean(type = "org.springframework.jdbc.core.JdbcTemplate")
@ConditionalOnProperty(prefix = "modality.sso.client-store", name = "store", havingValue = "jdbc", matchIfMissing = true)
public class SsoClientJdbcAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SsoClientRecordRepository.class)
    public JdbcSsoClientRecordRepository ssoClientRecordRepository(JdbcTemplate jdbcTemplate,
                                                                   SsoProperties properties) {
        return new JdbcSsoClientRecordRepository(jdbcTemplate, properties.getClientStore().getTableName(),
                properties.getClientStore().isAutoCreateTable());
    }
}
