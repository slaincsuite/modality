package cn.slainc.mfa.autoconfigure;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.core.context.UserContext;
import cn.slainc.mfa.api.MfaChallengeStore;
import cn.slainc.mfa.support.InMemoryMfaChallengeStore;
import cn.slainc.mfa.support.RedisMfaChallengeStore;
import cn.slainc.mfa.authentication.SecondFactorGate;
import cn.slainc.mfa.config.MfaConfigReader;
import cn.slainc.mfa.config.MfaProperties;
import cn.slainc.mfa.spi.MfaFactorPort;
import cn.slainc.mfa.spi.MfaUserLoader;
import cn.slainc.mfa.totp.OtpAuthUrlBuilder;
import cn.slainc.mfa.totp.TotpEngine;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MfaAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MfaAutoConfiguration.class));

    @Test
    void shouldRegisterTheMfaBeansByDefault() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SecondFactorGate.class);
            assertThat(context).hasSingleBean(MfaChallengeStore.class);
            assertThat(context).hasSingleBean(MfaConfigReader.class);
            assertThat(context).hasSingleBean(TotpEngine.class);
            assertThat(context).hasSingleBean(OtpAuthUrlBuilder.class);
            assertThat(context).hasSingleBean(MfaProperties.class);
            assertThat(context).doesNotHaveBean(MfaFactorPort.class);
            assertThat(context).doesNotHaveBean(MfaUserLoader.class);
        });
    }

    @Test
    void shouldRegisterNothingWhenTheModuleIsDisabled() {
        runner.withPropertyValues("modality.mfa.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(SecondFactorGate.class);
            assertThat(context).doesNotHaveBean(MfaChallengeStore.class);
            assertThat(context).doesNotHaveBean(MfaConfigReader.class);
        });
    }

    @Test
    void shouldAssembleTheGateWithAFactorPort() {
        MfaFactorPort factorPort = mock(MfaFactorPort.class);

        runner.withBean(MfaFactorPort.class, () -> factorPort).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SecondFactorGate.class);
            assertThat(context.getBean(MfaFactorPort.class)).isSameAs(factorPort);
        });
    }

    @Test
    void shouldFallBackToTheInMemoryChallengeStoreWithoutRedis() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(MfaChallengeStore.class)).isInstanceOf(InMemoryMfaChallengeStore.class);
        });
    }

    @Test
    void shouldPreferTheRedisChallengeStoreWhenRedisIsAvailable() {
        runner.withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class)).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(MfaChallengeStore.class)).isInstanceOf(RedisMfaChallengeStore.class);
        });
    }

    @Test
    void shouldBindTheOverriddenProperties() {
        runner.withPropertyValues(
                        "modality.mfa.issuer=acme",
                        "modality.mfa.digits=8",
                        "modality.mfa.period-seconds=60",
                        "modality.mfa.window=2",
                        "modality.mfa.challenge-ttl=PT2M",
                        "modality.mfa.recovery-code-count=3",
                        "modality.mfa.max-fail-count=7",
                        "modality.mfa.lock-duration=PT1M",
                        "modality.mfa.require-for-roles=admin,ops")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    MfaConfigReader config = context.getBean(MfaConfigReader.class);
                    assertThat(config.issuer()).isEqualTo("acme");
                    assertThat(config.digits()).isEqualTo(8);
                    assertThat(config.periodSeconds()).isEqualTo(60);
                    assertThat(config.window()).isEqualTo(2);
                    assertThat(config.challengeTtl()).isEqualTo(Duration.ofMinutes(2));
                    assertThat(config.recoveryCodeCount()).isEqualTo(3);
                    assertThat(config.maxFailCount()).isEqualTo(7);
                    assertThat(config.lockDuration()).isEqualTo(Duration.ofMinutes(1));
                    assertThat(config.requireForRoles()).containsExactly("admin", "ops");

                    TotpEngine totp = context.getBean(TotpEngine.class);
                    assertThat(totp.digits()).isEqualTo(8);
                    assertThat(totp.periodSeconds()).isEqualTo(60);
                });
    }

    @Test
    void shouldBackOffWhenACustomGateIsProvided() {
        SecondFactorGate custom = new SecondFactorGate() {

            @Override
            public boolean required(UserContext.UserInfo user) {
                return false;
            }

            @Override
            public String challenge(UserContext.UserInfo user) {
                return "custom";
            }

            @Override
            public UserContext.UserInfo verify(String challengeId, String code) {
                return null;
            }

            @Override
            public String method() {
                return "custom";
            }

            @Override
            public Duration challengeTtl() {
                return Duration.ofMinutes(1);
            }
        };

        runner.withBean(SecondFactorGate.class, () -> custom).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SecondFactorGate.class);
            assertThat(context.getBean(SecondFactorGate.class)).isSameAs(custom);
        });
    }

    @Test
    void shouldPreferTheDynamicPropertiesWhenAConfigServiceExists() {
        DynamicConfigService service = mock(DynamicConfigService.class);
        when(service.findString("modality.mfa.issuer")).thenReturn(Optional.of("dynamic"));
        when(service.getInt("modality.mfa.digits", 6)).thenReturn(8);

        runner.withBean(DynamicConfigService.class, () -> service).run(context -> {
            assertThat(context).hasNotFailed();
            MfaConfigReader config = context.getBean(MfaConfigReader.class);
            assertThat(config.issuer()).isEqualTo("dynamic");
            assertThat(config.digits()).isEqualTo(8);
            assertThat(context.getBean(MfaProperties.class).getIssuer()).isEqualTo("modality");
        });
    }
}
