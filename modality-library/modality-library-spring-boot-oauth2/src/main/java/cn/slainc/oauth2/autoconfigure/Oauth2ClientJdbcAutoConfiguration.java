package cn.slainc.oauth2.autoconfigure;

import cn.slainc.oauth2.client.ClientRecordRepository;
import cn.slainc.oauth2.client.JdbcClientRecordRepository;
import cn.slainc.oauth2.config.Oauth2Properties;
import cn.slainc.oauth2.consent.ConsentRecordStore;
import cn.slainc.oauth2.consent.JdbcConsentRecordStore;
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
@ConditionalOnProperty(prefix = "modality.oauth2.client", name = "store", havingValue = "jdbc", matchIfMissing = true)
public class Oauth2ClientJdbcAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ClientRecordRepository.class)
    public JdbcClientRecordRepository oauth2ClientRecordRepository(JdbcTemplate jdbcTemplate,
                                                                  Oauth2Properties properties) {
        return new JdbcClientRecordRepository(jdbcTemplate, properties.getClient().getTableName(),
                properties.getClient().isAutoCreateTable());
    }

    @Bean
    @ConditionalOnMissingBean(ConsentRecordStore.class)
    public JdbcConsentRecordStore oauth2ConsentRecordStore(JdbcTemplate jdbcTemplate,
                                                           Oauth2Properties properties) {
        return new JdbcConsentRecordStore(jdbcTemplate, properties.getClient().getConsentTableName(),
                properties.getClient().isAutoCreateTable());
    }
}
