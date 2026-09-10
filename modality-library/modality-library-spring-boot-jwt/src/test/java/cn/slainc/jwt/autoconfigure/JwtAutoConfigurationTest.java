package cn.slainc.jwt.autoconfigure;

import cn.slainc.jwt.config.JwtProperties;
import cn.slainc.jwt.issue.JwtTokenService;
import cn.slainc.jwt.jwks.JwksService;
import cn.slainc.jwt.key.JwtKeyManager;
import cn.slainc.jwt.revoke.MemoryRevocationStore;
import cn.slainc.jwt.revoke.RedisRevocationStore;
import cn.slainc.jwt.revoke.RevocationStore;
import cn.slainc.jwt.rotate.JwtKeyRotationScheduler;
import cn.slainc.jwt.verify.JwtVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JwtRedisStoreAutoConfiguration.class, JwtAutoConfiguration.class));

    @Test
    void 默认装配全部核心Bean() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(JwtProperties.class);
            assertThat(context).hasSingleBean(JwtKeyManager.class);
            assertThat(context).hasSingleBean(JwtTokenService.class);
            assertThat(context).hasSingleBean(JwtVerifier.class);
            assertThat(context).hasSingleBean(JwksService.class);
            assertThat(context).hasBean("refreshTokenService");
            assertThat(context).hasBean("introspectionService");
            assertThat(context).hasBean("revocationService");
            assertThat(context).doesNotHaveBean(JwtKeyRotationScheduler.class);
        });
    }

    @Test
    void 无Redis模板时吊销存储回落内存() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(RevocationStore.class);
            assertThat(context.getBean(RevocationStore.class)).isInstanceOf(MemoryRevocationStore.class);
            assertThat(context).doesNotHaveBean(RedisRevocationStore.class);
        });
    }

    @Test
    void 有Redis模板且store为redis时装配Redis存储() {
        runner.withBean(StringRedisTemplate.class, JwtAutoConfigurationTest::mockRedisTemplate)
                .run(context -> {
                    assertThat(context.getBean(RevocationStore.class)).isInstanceOf(RedisRevocationStore.class);
                });
    }

    @Test
    void store配置为memory时强制使用内存存储() {
        runner.withBean(StringRedisTemplate.class, JwtAutoConfigurationTest::mockRedisTemplate)
                .withPropertyValues("modality.jwt.revocation-store=memory")
                .run(context -> {
                    assertThat(context.getBean(RevocationStore.class)).isInstanceOf(MemoryRevocationStore.class);
                });
    }

    private static StringRedisTemplate mockRedisTemplate() {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(org.mockito.Mockito.mock(
                org.springframework.data.redis.connection.RedisConnectionFactory.class));
        template.afterPropertiesSet();
        return template;
    }

    @Test
    void enabled为false时不装配任何Bean() {
        runner.withPropertyValues("modality.jwt.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(JwtKeyManager.class);
                    assertThat(context).doesNotHaveBean(JwtTokenService.class);
                    assertThat(context).doesNotHaveBean(JwksService.class);
                });
    }

    @Test
    void 开启autoRotate后装配调度器() {
        runner.withPropertyValues("modality.jwt.auto-rotate.enabled=true",
                        "modality.jwt.auto-rotate.interval-hours=1")
                .run(context -> {
                    assertThat(context).hasSingleBean(JwtKeyRotationScheduler.class);
                });
    }

    @Test
    void 自定义JwtKeyManager时自动配置退避() {
        runner.withBean(JwtKeyManager.class, () -> new JwtKeyManager(new cn.slainc.jwt.config.JwtProperties()))
                .run(context -> {
                    assertThat(context).hasSingleBean(JwtKeyManager.class);
                    assertThat(context.getBean(JwtKeyManager.class).currentKid()).startsWith("kid-");
                });
    }

    @Test
    void 自定义RevocationStore时内存兜底退避() {
        runner.withBean(RevocationStore.class, () -> new RevocationStore() {
                    @Override
                    public void revoke(String jti, java.time.Instant expiresAt) {
                    }

                    @Override
                    public boolean isRevoked(String jti) {
                        return false;
                    }
                })
                .run(context -> {
                    assertThat(context).hasSingleBean(RevocationStore.class);
                    assertThat(context).doesNotHaveBean(MemoryRevocationStore.class);
                    assertThat(context.getBean(RevocationStore.class).isRevoked("x")).isFalse();
                });
    }

    @Test
    void 默认issuer为modality且白名单为三算法() {
        runner.run(context -> {
            JwtProperties properties = context.getBean(JwtProperties.class);
            assertThat(properties.getIssuer()).isEqualTo("modality");
            assertThat(properties.getAllowedAlgorithms())
                    .containsExactly(cn.slainc.crypto.jwt.JwtAlgorithm.HS256,
                            cn.slainc.crypto.jwt.JwtAlgorithm.RS256,
                            cn.slainc.crypto.jwt.JwtAlgorithm.ES256);
            assertThat(properties.getRetainedKeys()).isEqualTo(3);
            assertThat(properties.getClockSkewSeconds()).isEqualTo(60);
            assertThat(properties.getMaxTokenLength()).isEqualTo(4096);
            assertThat(properties.getJwksCacheSeconds()).isEqualTo(600);
            assertThat(properties.getTtlAccess()).isEqualTo(java.time.Duration.ofHours(2));
            assertThat(properties.getTtlRefresh()).isEqualTo(java.time.Duration.ofDays(30));
            assertThat(properties.getTtlIdToken()).isEqualTo(java.time.Duration.ofMinutes(10));
            assertThat(properties.getTtlOneShot()).isEqualTo(java.time.Duration.ofMinutes(5));
            assertThat(properties.getAutoRotate().isEnabled()).isFalse();
            assertThat(properties.getAutoRotate().getIntervalHours()).isEqualTo(720);
        });
    }
}
