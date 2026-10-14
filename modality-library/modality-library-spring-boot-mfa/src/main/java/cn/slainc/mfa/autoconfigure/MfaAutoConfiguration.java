package cn.slainc.mfa.autoconfigure;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.crypto.key.CryptoKeyProvider;
import cn.slainc.mfa.api.MfaChallengeStore;
import cn.slainc.mfa.support.InMemoryMfaChallengeStore;
import cn.slainc.mfa.support.RedisMfaChallengeStore;
import cn.slainc.mfa.authentication.SecondFactorGate;
import cn.slainc.mfa.config.MfaConfigReader;
import cn.slainc.mfa.config.MfaProperties;
import cn.slainc.mfa.recovery.RecoveryCodeGenerator;
import cn.slainc.mfa.spi.MfaFactorPort;
import cn.slainc.mfa.spi.MfaUserLoader;
import cn.slainc.mfa.support.MfaSecondFactorGate;
import cn.slainc.mfa.support.MfaSecretCodec;
import cn.slainc.mfa.totp.OtpAuthUrlBuilder;
import cn.slainc.mfa.totp.TotpEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

@AutoConfiguration
@AutoConfigureAfter(name = {"org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration",
        "cn.slainc.crypto.autoconfigure.CryptoAutoConfiguration"})
@ConditionalOnProperty(prefix = "modality.mfa", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(MfaProperties.class)
public class MfaAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MfaAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(MfaConfigReader.class)
    public MfaConfigReader mfaConfigReader(MfaProperties properties,
                                           ObjectProvider<DynamicConfigService> configService) {
        return new MfaConfigReader(properties, configService);
    }

    @Bean
    @ConditionalOnMissingBean(TotpEngine.class)
    public TotpEngine totpEngine(MfaConfigReader configReader) {
        return new TotpEngine(configReader);
    }

    @Bean
    @ConditionalOnMissingBean(OtpAuthUrlBuilder.class)
    public OtpAuthUrlBuilder otpAuthUrlBuilder() {
        return new OtpAuthUrlBuilder();
    }

    @Bean
    @ConditionalOnMissingBean(RecoveryCodeGenerator.class)
    public RecoveryCodeGenerator recoveryCodeGenerator() {
        return new RecoveryCodeGenerator();
    }

    @Bean
    @ConditionalOnMissingBean(MfaSecretCodec.class)
    public MfaSecretCodec mfaSecretCodec(ObjectProvider<CryptoKeyProvider> keyProvider) {
        return new MfaSecretCodec(keyProvider.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean(MfaChallengeStore.class)
    public MfaChallengeStore mfaChallengeStore(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        InMemoryMfaChallengeStore memoryStore = new InMemoryMfaChallengeStore();
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            log.warn("modality.mfa：未发现 StringRedisTemplate，二次验证挑战回落到进程内存，多实例部署将无法完成二次验证");
            return memoryStore;
        }
        return new RedisMfaChallengeStore(redisTemplate, memoryStore);
    }

    @Bean
    @ConditionalOnMissingBean(SecondFactorGate.class)
    public SecondFactorGate mfaSecondFactorGate(ObjectProvider<MfaFactorPort> factorPortProvider,
                                                MfaChallengeStore challengeStore, MfaConfigReader configReader,
                                                MfaSecretCodec secretCodec, TotpEngine totp,
                                                RecoveryCodeGenerator recoveryCodeGenerator,
                                                ObjectProvider<MfaUserLoader> userLoader) {
        return new MfaSecondFactorGate(factorPortProvider, challengeStore, configReader, secretCodec, totp,
                recoveryCodeGenerator, userLoader);
    }
}
