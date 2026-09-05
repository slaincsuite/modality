package cn.slainc.sensitive.autoconfigure;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.crypto.key.CryptoKeyProvider;
import cn.slainc.sensitive.core.SensitiveConfig;
import cn.slainc.sensitive.core.SensitiveMasker;
import cn.slainc.sensitive.core.SensitiveSwitch;
import cn.slainc.sensitive.jackson.SensitiveJacksonCustomizer;
import cn.slainc.sensitive.metrics.SensitiveMetrics;
import cn.slainc.web.autoconfigure.WebAutoConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

// 必须排在 WebAutoConfiguration 之后：web 的 JsonMapperBuilderCustomizer 用的是不带参数的 @ConditionalOnMissingBean，
// 若本模块先注册，web 的定制会静默退避，Long 精度保护随之失效
@AutoConfiguration(after = WebAutoConfiguration.class)
@ConditionalOnProperty(prefix = "modality.sensitive", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SensitiveProperties.class)
public class SensitiveAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SensitiveConfig.class)
    public SensitiveConfig sensitiveConfig(SensitiveProperties properties) {
        SensitiveProperties.Mask mask = properties.getMask();
        return new SensitiveConfig(mask.getMaskChar(), mask.getReplacement(), mask.getHashAlgorithm(),
                mask.isCycleSafe());
    }

    @Bean
    @ConditionalOnMissingBean(SensitiveMetrics.class)
    public SensitiveMetrics sensitiveMetrics() {
        return SensitiveMetrics.noop();
    }

    @Bean
    @ConditionalOnMissingBean(SensitiveSwitch.class)
    public SensitiveSwitch sensitiveSwitch(SensitiveProperties properties,
                                           ObjectProvider<DynamicConfigService> dynamicConfigService) {
        return new SensitiveSwitch(properties.isEnabled(), dynamicConfigService::getIfAvailable);
    }

    @Bean
    @ConditionalOnMissingBean(SensitiveMasker.class)
    public SensitiveMasker sensitiveMasker(SensitiveConfig sensitiveConfig, SensitiveMetrics sensitiveMetrics,
                                           SensitiveSwitch sensitiveSwitch,
                                           ObjectProvider<CryptoKeyProvider> cryptoKeyProvider) {
        return new SensitiveMasker(sensitiveConfig, sensitiveMetrics, sensitiveSwitch, cryptoKeyProvider::getIfAvailable);
    }

    @Bean
    @ConditionalOnMissingBean(SensitiveJacksonCustomizer.class)
    public SensitiveJacksonCustomizer sensitiveJacksonCustomizer(SensitiveMasker sensitiveMasker,
                                                                 SensitiveConfig sensitiveConfig) {
        return new SensitiveJacksonCustomizer(sensitiveMasker, sensitiveConfig);
    }
}
