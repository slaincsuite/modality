package cn.slainc.modules.auth.autoconfigure;

import cn.slainc.crypto.key.CryptoKeyProvider;
import cn.slainc.modules.auth.service.third.FeishuAppConfigService;
import cn.slainc.modules.auth.service.third.FeishuContactSyncService;
import cn.slainc.modules.auth.service.user.TargetUserGuard;
import cn.slainc.modules.auth.store.FeishuAppConfigStore;
import cn.slainc.modules.auth.store.FeishuSyncRecordStore;
import cn.slainc.modules.auth.support.ThirdSecretCodec;
import cn.slainc.modules.auth.web.third.FeishuAppConfigController;
import cn.slainc.modules.auth.web.third.UserThirdBindingManageController;
import cn.slainc.thirdauth.ThirdAuthService;
import cn.slainc.thirdauth.config.ThirdAuthProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@AutoConfiguration(after = {AuthSecurityPolicyAutoConfiguration.class, AuthThirdAutoConfiguration.class})
@ConditionalOnClass(name = {"cn.slainc.thirdauth.ThirdAuthService",
        "org.springframework.jdbc.core.JdbcTemplate"})
@ConditionalOnBean({ThirdAuthService.class, JdbcTemplate.class, TargetUserGuard.class})
@ConditionalOnProperty(prefix = "modality.auth.third-admin", name = "enabled", matchIfMissing = true)
@Import({FeishuAppConfigController.class, UserThirdBindingManageController.class})
public class AuthThirdAdminAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ThirdSecretCodec thirdSecretCodec(ObjectProvider<CryptoKeyProvider> keyProvider) {
        return new ThirdSecretCodec(keyProvider.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    public FeishuAppConfigStore feishuAppConfigStore(JdbcTemplate jdbcTemplate) {
        return new FeishuAppConfigStore(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public FeishuAppConfigService feishuAppConfigService(FeishuAppConfigStore store, ThirdSecretCodec secretCodec,
                                                         ThirdAuthProperties properties) {
        return new FeishuAppConfigService(store, secretCodec, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public FeishuSyncRecordStore feishuSyncRecordStore(JdbcTemplate jdbcTemplate) {
        return new FeishuSyncRecordStore(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public FeishuContactSyncService feishuContactSyncService(FeishuAppConfigService configService,
                                                             FeishuSyncRecordStore syncRecordStore,
                                                             ThirdAuthProperties properties) {
        return new FeishuContactSyncService(configService, syncRecordStore, properties);
    }
}
