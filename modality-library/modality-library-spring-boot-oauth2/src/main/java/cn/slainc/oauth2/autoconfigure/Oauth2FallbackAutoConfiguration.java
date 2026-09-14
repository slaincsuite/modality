package cn.slainc.oauth2.autoconfigure;

import cn.slainc.oauth2.client.ClientRecordRepository;
import cn.slainc.oauth2.client.InMemoryClientRecordRepository;
import cn.slainc.oauth2.consent.ConsentRecordStore;
import cn.slainc.oauth2.consent.InMemoryConsentRecordStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = Oauth2ClientJdbcAutoConfiguration.class)
@ConditionalOnProperty(prefix = "modality.oauth2", name = "enabled", matchIfMissing = true)
public class Oauth2FallbackAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ClientRecordRepository.class)
    public InMemoryClientRecordRepository oauth2ClientRecordRepository() {
        return new InMemoryClientRecordRepository();
    }

    @Bean
    @ConditionalOnMissingBean(ConsentRecordStore.class)
    public InMemoryConsentRecordStore oauth2ConsentRecordStore() {
        return new InMemoryConsentRecordStore();
    }
}
