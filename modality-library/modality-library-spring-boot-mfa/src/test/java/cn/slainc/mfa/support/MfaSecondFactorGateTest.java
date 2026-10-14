package cn.slainc.mfa.support;

import cn.slainc.core.context.UserContext;
import cn.slainc.core.enums.LoginType;
import cn.slainc.core.exception.BizException;
import cn.slainc.mfa.support.InMemoryMfaChallengeStore;
import cn.slainc.mfa.config.MfaConfigReader;
import cn.slainc.mfa.config.MfaProperties;
import cn.slainc.mfa.recovery.RecoveryCodeGenerator;
import cn.slainc.mfa.spi.MfaFactorPort;
import cn.slainc.mfa.spi.MfaFactorSnapshot;
import cn.slainc.mfa.spi.MfaUserLoader;
import cn.slainc.mfa.totp.TotpEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MfaSecondFactorGateTest {

    private final MfaProperties properties = new MfaProperties();

    private final MfaConfigReader config = new MfaConfigReader(properties, null);

    private final InMemoryMfaFactorPort factorPort = new InMemoryMfaFactorPort();

    private final InMemoryMfaChallengeStore challengeStore = new InMemoryMfaChallengeStore();

    private final TotpEngine totp = new TotpEngine(config);

    private final RecoveryCodeGenerator recoveryCodeGenerator = new RecoveryCodeGenerator();

    private final String secret = totp.generateSecretKey();

    private MfaSecondFactorGate gate;

    @BeforeEach
    void setUp() {
        gate = new MfaSecondFactorGate(providerOf(factorPort), challengeStore, config,
                new MfaSecretCodec(null), totp, recoveryCodeGenerator,
                providerOf(new StubUserLoader()));
    }

    @Test
    void shouldRequireTheSecondFactorForAnActivatedFactor() {
        factorPort.save(factor(true, null, 0, null));

        assertThat(gate.required(user(Set.of("user")))).isTrue();
    }

    @Test
    void shouldNotRequireTheSecondFactorWhenItIsNotActivated() {
        factorPort.save(factor(false, null, 0, null));

        assertThat(gate.required(user(Set.of("user")))).isFalse();
        assertThat(gate.required(null)).isFalse();
        assertThat(gate.required(new UserContext.UserInfo(null, "ghost", null, null, null, false))).isFalse();
        assertThat(gate.required(user(Set.of("user")))).isFalse();
    }

    @Test
    void shouldNotRequireTheSecondFactorWithoutAFactorPort() {
        MfaSecondFactorGate isolated = new MfaSecondFactorGate(providerOf(null), challengeStore, config,
                new MfaSecretCodec(null), totp, recoveryCodeGenerator,
                providerOf(new StubUserLoader()));

        assertThat(isolated.required(user(Set.of("user")))).isFalse();
    }

    @Test
    void shouldForceTheSecondFactorForTheConfiguredRoles() {
        properties.setRequireForRoles(List.of("admin"));

        assertThat(gate.required(user(Set.of("admin")))).isTrue();
        assertThat(gate.required(user(Set.of("user")))).isFalse();
    }

    @Test
    void shouldFailOpenWhenTheStatusCannotBeEvaluated() {
        MfaFactorPort failing = mock(MfaFactorPort.class);
        when(failing.find(1L, "TOTP")).thenThrow(new IllegalStateException("database down"));
        MfaSecondFactorGate isolated = new MfaSecondFactorGate(providerOf(failing), challengeStore, config,
                new MfaSecretCodec(null), totp, recoveryCodeGenerator,
                providerOf(new StubUserLoader()));

        assertThat(isolated.required(user(Set.of("user")))).isFalse();
    }

    @Test
    void shouldIssueAOneTimeChallenge() {
        String challengeId = gate.challenge(user(Set.of("user")));

        assertThat(challengeId).hasSize(32);
        assertThat(challengeStore.peek(challengeId)).isNotNull();
        assertThat(challengeStore.peek(challengeId).userId()).isEqualTo(1L);
        assertThat(challengeStore.peek(challengeId).method()).isEqualTo("TOTP");
        assertThat(challengeStore.consume(challengeId)).isNotNull();
        assertThat(challengeStore.consume(challengeId)).isNull();
    }

    @Test
    void shouldVerifyTheCodeAndReloadTheUser() {
        factorPort.save(factor(true, null, 0, null));
        String challengeId = gate.challenge(user(Set.of("user")));

        UserContext.UserInfo verified = gate.verify(challengeId, code());

        assertThat(verified.userId()).isEqualTo(1L);
        assertThat(verified.username()).isEqualTo("alice");
        MfaFactorSnapshot stored = factorPort.find(1L, "TOTP");
        assertThat(stored.failCount()).isZero();
        assertThat(stored.lastUsedAt()).isNotNull();
    }

    @Test
    void shouldFallBackToARecoveryCode() {
        factorPort.save(factor(true, recoveryCodeGenerator.hash("AAAA-BBBB"), 2, null));
        String challengeId = gate.challenge(user(Set.of("user")));

        assertThat(gate.verify(challengeId, "AAAA-BBBB").userId()).isEqualTo(1L);

        MfaFactorSnapshot stored = factorPort.find(1L, "TOTP");
        assertThat(stored.recoveryCodes()).isNull();
        assertThat(stored.failCount()).isZero();
        assertThat(stored.lastUsedAt()).isNotNull();
    }

    @Test
    void shouldRejectAnUnknownOrBlankChallenge() {
        assertThatThrownBy(() -> gate.verify("missing", "123456"))
                .isInstanceOf(BizException.class)
                .hasMessage("二次验证已失效，请重新登录");
        assertThatThrownBy(() -> gate.verify(" ", "123456"))
                .isInstanceOf(BizException.class)
                .hasMessage("挑战标识不能为空");
    }

    @Test
    void shouldRejectAnInvalidCodeAndBurnTheChallenge() {
        factorPort.save(factor(true, null, 0, null));
        String challengeId = gate.challenge(user(Set.of("user")));

        assertThatThrownBy(() -> gate.verify(challengeId, "000000"))
                .isInstanceOf(BizException.class)
                .hasMessage("动态码或恢复码错误");
        assertThat(challengeStore.peek(challengeId)).isNull();
        assertThat(factorPort.find(1L, "TOTP").failCount()).isEqualTo(1);
    }

    @Test
    void shouldLockTheFactorAfterTooManyFailures() {
        properties.setMaxFailCount(2);
        factorPort.save(factor(true, null, 0, null));

        assertThatThrownBy(() -> gate.verify(gate.challenge(user(Set.of("user"))), "000000"))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> gate.verify(gate.challenge(user(Set.of("user"))), "000000"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("锁定");

        MfaFactorSnapshot stored = factorPort.find(1L, "TOTP");
        assertThat(stored.failCount()).isZero();
        assertThat(stored.lockedUntil()).isNotNull();
    }

    @Test
    void shouldReportALockedFactor() {
        factorPort.save(factor(true, null, 0, LocalDateTime.now().plusMinutes(5)));
        String challengeId = gate.challenge(user(Set.of("user")));

        assertThatThrownBy(() -> gate.verify(challengeId, code()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("锁定");
        assertThat(factorPort.find(1L, "TOTP").failCount()).isZero();
    }

    @Test
    void shouldRejectAnAccountWithoutAnActivatedFactor() {
        String challengeId = gate.challenge(user(Set.of("user")));

        assertThatThrownBy(() -> gate.verify(challengeId, code()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("尚未完成二次验证绑定");
    }

    @Test
    void shouldRejectWhenNoFactorPortIsAvailable() {
        MfaSecondFactorGate isolated = new MfaSecondFactorGate(providerOf(null), challengeStore, config,
                new MfaSecretCodec(null), totp, recoveryCodeGenerator,
                providerOf(new StubUserLoader()));
        String challengeId = isolated.challenge(user(Set.of("user")));

        assertThatThrownBy(() -> isolated.verify(challengeId, code()))
                .isInstanceOf(BizException.class)
                .hasMessage("未启用二次验证");
    }

    @Test
    void shouldRejectWhenNoUserLoaderIsAvailable() {
        MfaSecondFactorGate isolated = new MfaSecondFactorGate(providerOf(factorPort), challengeStore, config,
                new MfaSecretCodec(null), totp, recoveryCodeGenerator, providerOf(null));
        factorPort.save(factor(true, null, 0, null));
        String challengeId = isolated.challenge(user(Set.of("user")));

        assertThatThrownBy(() -> isolated.verify(challengeId, code()))
                .isInstanceOf(BizException.class)
                .hasMessage("未提供用户信息加载器，无法完成二次验证");
    }

    @Test
    void shouldExposeTheMethodAndTheConfiguredTtl() {
        assertThat(gate.method()).isEqualTo("TOTP");
        assertThat(gate.challengeTtl()).isEqualTo(Duration.ofMinutes(5));

        properties.setChallengeTtl(Duration.ofMinutes(2));

        assertThat(gate.challengeTtl()).isEqualTo(Duration.ofMinutes(2));

        properties.setChallengeTtl(Duration.ZERO);

        assertThat(gate.challengeTtl()).isEqualTo(Duration.ofMinutes(5));
    }

    private MfaFactorSnapshot factor(boolean activated, String recoveryCodes, int failCount,
                                     LocalDateTime lockedUntil) {
        return new MfaFactorSnapshot(1L, 1L, "TOTP", secret, null, activated, recoveryCodes, null, failCount,
                lockedUntil, null);
    }

    private String code() {
        return totp.generate(secret);
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> providerOf(T bean) {
        ObjectProvider<T> objectProvider = mock(ObjectProvider.class);
        when(objectProvider.getIfAvailable()).thenReturn(bean);
        return objectProvider;
    }

    private static UserContext.UserInfo user(Set<String> roles) {
        return new UserContext.UserInfo(1L, "alice", LoginType.PLATFORM_USER, roles, Set.of(), false);
    }

    private static final class StubUserLoader implements MfaUserLoader {

        @Override
        public UserContext.UserInfo loadByUserId(Long userId) {
            return user(Set.of("user"));
        }
    }

    private static final class InMemoryMfaFactorPort implements MfaFactorPort {

        private final Map<String, Factor> factors = new LinkedHashMap<>();

        void save(MfaFactorSnapshot snapshot) {
            factors.put(key(snapshot.userId(), snapshot.factorType()), new Factor(snapshot));
        }

        @Override
        public MfaFactorSnapshot find(Long userId, String factorType) {
            Factor factor = factors.get(key(userId, factorType));
            return factor == null ? null : factor.snapshot();
        }

        @Override
        public Long insert(MfaFactorSnapshot factor) {
            save(factor);
            return factor.id();
        }

        @Override
        public void updateActivation(Long userId, String factorType, boolean activated) {
            Factor factor = factors.get(key(userId, factorType));
            if (factor != null) {
                factor.activated = activated;
            }
        }

        @Override
        public void updateFailure(Long userId, String factorType, int failCount, LocalDateTime lockedUntil) {
            Factor factor = factors.get(key(userId, factorType));
            if (factor != null) {
                factor.failCount = failCount;
                factor.lockedUntil = lockedUntil;
            }
        }

        @Override
        public void updateSuccess(Long userId, String factorType, LocalDateTime lastUsedAt) {
            Factor factor = factors.get(key(userId, factorType));
            if (factor != null) {
                factor.failCount = 0;
                factor.lockedUntil = null;
                factor.lastUsedAt = lastUsedAt;
            }
        }

        @Override
        public void updateRecoveryCodes(Long userId, String factorType, String recoveryCodes) {
            Factor factor = factors.get(key(userId, factorType));
            if (factor != null) {
                factor.recoveryCodes = recoveryCodes;
            }
        }

        @Override
        public boolean delete(Long userId, String factorType) {
            return factors.remove(key(userId, factorType)) != null;
        }

        private static String key(Long userId, String factorType) {
            return userId + ":" + factorType;
        }

        private static final class Factor {

            private final Long id;
            private final Long userId;
            private final String factorType;
            private final String secretCipher;
            private final String secretKeyId;
            private boolean activated;
            private String recoveryCodes;
            private LocalDateTime lastUsedAt;
            private int failCount;
            private LocalDateTime lockedUntil;
            private final Long tenantId;

            private Factor(MfaFactorSnapshot snapshot) {
                this.id = snapshot.id();
                this.userId = snapshot.userId();
                this.factorType = snapshot.factorType();
                this.secretCipher = snapshot.secretCipher();
                this.secretKeyId = snapshot.secretKeyId();
                this.activated = Boolean.TRUE.equals(snapshot.activated());
                this.recoveryCodes = snapshot.recoveryCodes();
                this.lastUsedAt = snapshot.lastUsedAt();
                this.failCount = snapshot.failCount() == null ? 0 : snapshot.failCount();
                this.lockedUntil = snapshot.lockedUntil();
                this.tenantId = snapshot.tenantId();
            }

            private MfaFactorSnapshot snapshot() {
                return new MfaFactorSnapshot(id, userId, factorType, secretCipher, secretKeyId, activated,
                        recoveryCodes, lastUsedAt, failCount, lockedUntil, tenantId);
            }
        }
    }
}
