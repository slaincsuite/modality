package cn.slainc.sso.autoconfigure;

import cn.slainc.sso.client.InMemorySsoClientRecordRepository;
import cn.slainc.sso.client.SsoClientRecordRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = SsoClientJdbcAutoConfiguration.class)
@ConditionalOnProperty(prefix = "modality.sso", name = "enabled", matchIfMissing = true)
public class SsoFallbackAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SsoClientRecordRepository.class)
    public InMemorySsoClientRecordRepository ssoClientRecordRepository() {
        return new InMemorySsoClientRecordRepository();
    }
}
