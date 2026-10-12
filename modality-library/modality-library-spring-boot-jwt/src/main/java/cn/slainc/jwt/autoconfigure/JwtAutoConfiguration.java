package cn.slainc.jwt.autoconfigure;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.jwt.algorithm.JwtAlgorithmWhitelist;
import cn.slainc.jwt.config.JwtProperties;
import cn.slainc.jwt.config.JwtSettings;
import cn.slainc.jwt.introspect.IntrospectionService;
import cn.slainc.jwt.issue.JwtTokenService;
import cn.slainc.jwt.jwks.JwksService;
import cn.slainc.jwt.key.JwtKeyManager;
import cn.slainc.jwt.key.JwtKeyPersistence;
import cn.slainc.jwt.refresh.RefreshTokenService;
import cn.slainc.jwt.revoke.MemoryRevocationStore;
import cn.slainc.jwt.revoke.RevocationService;
import cn.slainc.jwt.revoke.RevocationStore;
import cn.slainc.jwt.rotate.JwtKeyRotationScheduler;
import cn.slainc.jwt.verify.JwtVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

@AutoConfiguration(after = JwtRedisStoreAutoConfiguration.class)
@EnableConfigurationProperties(JwtProperties.class)
@ConditionalOnProperty(prefix = JwtProperties.PREFIX, name = "enabled", matchIfMissing = true)
public class JwtAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(JwtAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(JwtAlgorithmWhitelist.class)
    public JwtAlgorithmWhitelist jwtAlgorithmWhitelist(JwtProperties properties) {
        return new JwtAlgorithmWhitelist(properties.getAllowedAlgorithms());
    }

    @Bean
    @ConditionalOnMissingBean(JwtSettings.class)
    public JwtSettings jwtSettings(JwtProperties properties, ObjectProvider<DynamicConfigService> dynamicConfigService) {
        return new JwtSettings(properties, dynamicConfigService);
    }

    @Bean
    @ConditionalOnMissingBean(JwtKeyManager.class)
    public JwtKeyManager jwtKeyManager(JwtProperties properties, ObjectProvider<JwtKeyPersistence> persistence) {
        return new JwtKeyManager(properties, persistence.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean(JwtTokenService.class)
    public JwtTokenService jwtTokenService(JwtKeyManager keyManager, JwtAlgorithmWhitelist whitelist,
                                           JwtSettings settings) {
        return new JwtTokenService(keyManager, whitelist, settings);
    }

    @Bean
    @ConditionalOnMissingBean(RevocationService.class)
    public RevocationService revocationService(RevocationStore revocationStore,
                                               ObjectProvider<JwtVerifier> jwtVerifier) {
        // 延迟取 JwtVerifier：它反过来依赖本 Bean，构造期不能成环
        return new RevocationService(revocationStore, token -> jwtVerifier.getObject().verifyStrict(token));
    }

    @Bean
    @ConditionalOnMissingBean(JwtVerifier.class)
    public JwtVerifier jwtVerifier(JwtKeyManager keyManager, JwtAlgorithmWhitelist whitelist, JwtSettings settings,
                                   RevocationService revocationService) {
        return new JwtVerifier(keyManager, whitelist, settings, revocationService);
    }

    @Bean
    @ConditionalOnMissingBean(RefreshTokenService.class)
    public RefreshTokenService refreshTokenService(JwtVerifier verifier, JwtTokenService tokenService,
                                                   RevocationService revocationService, JwtSettings settings) {
        return new RefreshTokenService(verifier, tokenService, revocationService, settings);
    }

    @Bean
    @ConditionalOnMissingBean(IntrospectionService.class)
    public IntrospectionService introspectionService(JwtVerifier verifier) {
        return new IntrospectionService(verifier);
    }

    @Bean
    @ConditionalOnMissingBean(JwksService.class)
    public JwksService jwksService(JwtKeyManager keyManager, JwtSettings settings,
                                   ObjectProvider<StringRedisTemplate> redisTemplate) {
        return new JwksService(keyManager, settings, redisTemplate.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean(RevocationStore.class)
    public MemoryRevocationStore memoryRevocationStore() {
        log.info("未装配 Redis 吊销存储，jwt 吊销记录保存在进程内存，重启后失效");
        return new MemoryRevocationStore();
    }

    @Bean
    @ConditionalOnMissingBean(JwtKeyRotationScheduler.class)
    @ConditionalOnProperty(prefix = "modality.jwt.auto-rotate", name = "enabled", matchIfMissing = false)
    public JwtKeyRotationScheduler jwtKeyRotationScheduler(JwtKeyManager keyManager, JwtProperties properties) {
        return new JwtKeyRotationScheduler(keyManager, Duration.ofHours(properties.getAutoRotate().getIntervalHours()));
    }
}
