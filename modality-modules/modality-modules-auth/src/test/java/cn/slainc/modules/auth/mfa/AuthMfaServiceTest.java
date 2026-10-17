package cn.slainc.modules.auth.mfa;

import cn.slainc.core.context.UserContext;
import cn.slainc.core.enums.LoginType;
import cn.slainc.core.exception.BizException;
import cn.slainc.mfa.authentication.SecondFactorGate;
import cn.slainc.mfa.config.MfaConfigReader;
import cn.slainc.mfa.config.MfaProperties;
import cn.slainc.mfa.recovery.RecoveryCodeGenerator;
import cn.slainc.mfa.spi.MfaFactorSnapshot;
import cn.slainc.mfa.support.MfaSecretCodec;
import cn.slainc.mfa.totp.TotpEngine;
import cn.slainc.modules.auth.service.login.AuthLoginService;
import cn.slainc.modules.auth.service.mfa.AuthMfaService;
import cn.slainc.modules.auth.service.policy.SecurityPolicyService;
import cn.slainc.modules.auth.service.session.AuthSessionService;
import cn.slainc.modules.auth.store.AuthTicketStore;
import cn.slainc.modules.auth.store.MfaFactorStore;
import cn.slainc.security.login.LoginResult;
import cn.slainc.security.spi.AccountAuthProvider;
import cn.slainc.security.spi.PasswordAccount;
import cn.slainc.security.spi.SecurityPermissionProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthMfaServiceTest {

    private static final Long USER_ID = 1L;

    private final MfaProperties properties = new MfaProperties();

    private final MfaConfigReader config = new MfaConfigReader(properties, null);

    private final TotpEngine totp = new TotpEngine(config);

    private final RecoveryCodeGenerator recoveryCodeGenerator = new RecoveryCodeGenerator();

    private final InMemoryMfaFactorStore factorStore = new InMemoryMfaFactorStore();

    private final StubGate gate = new StubGate();

    private final AuthTicketStore<MfaLoginTicket> ticketStore = AuthTicketStore.of(null,
            "test:mfa:", MfaLoginTicket::getChallengeId, MfaLoginTicket.class);

    private final AuthSessionService sessionService = mock(AuthSessionService.class);

    private final AuthLoginService loginService = mock(AuthLoginService.class);

    private AuthMfaService service;

    @BeforeEach
    void setUp() {
        service = new AuthMfaService(config, totp, recoveryCodeGenerator, new MfaSecretCodec(null), gate,
                factorStore, ticketStore, sessionService, loginService, providerOf(new StubAccountProvider()),
                null);
    }

    @Test
    void shouldEnrollWithoutActivatingTheFactor() {
        AuthMfaService.MfaEnrollView result = service.enroll(LoginType.TENANT_USER, USER_ID);

        assertThat(result.secret()).hasSize(32);
        assertThat(result.issuer()).isEqualTo("modality");
        assertThat(result.digits()).isEqualTo(6);
        assertThat(result.periodSeconds()).isEqualTo(30);
        assertThat(result.otpauthUrl()).isEqualTo("otpauth://totp/modality:alice?secret=" + result.secret()
                + "&issuer=modality&algorithm=SHA1&digits=6&period=30");

        AuthMfaService.MfaStatusView status = service.status(LoginType.TENANT_USER, USER_ID);
        assertThat(status.enrolled()).isTrue();
        assertThat(status.activated()).isFalse();

        MfaFactorRecord stored = factorStore.raw(USER_ID);
        assertThat(stored.getSecretCipher()).isEqualTo(result.secret());
        assertThat(stored.getActivated()).isFalse();
        assertThat(stored.getFailCount()).isZero();
        assertThat(stored.getLoginType()).isEqualTo("TENANT_USER");
        assertThat(stored.getUsername()).isEqualTo("alice");
    }

    @Test
    void shouldActivateWithAValidCode() {
        AuthMfaService.MfaEnrollView result = service.enroll(LoginType.TENANT_USER, USER_ID);

        service.activate(LoginType.TENANT_USER, USER_ID, code(result.secret()));

        AuthMfaService.MfaStatusView status = service.status(LoginType.TENANT_USER, USER_ID);
        assertThat(status.activated()).isTrue();
        assertThat(status.recoveryCodesRemaining()).isZero();
        assertThat(factorStore.raw(USER_ID).getLastUsedAt()).isNotNull();
    }

    @Test
    void loginProtectSwitchCanDisableSecondFactorRequirement() {
        SecurityPolicyService policy = mock(SecurityPolicyService.class);
        when(policy.loginProtectEnabled()).thenReturn(false);
        @SuppressWarnings("unchecked")
        ObjectProvider<SecurityPolicyService> policyProvider = mock(ObjectProvider.class);
        when(policyProvider.getIfAvailable()).thenReturn(policy);
        AuthMfaService guarded = new AuthMfaService(config, totp, recoveryCodeGenerator, new MfaSecretCodec(null),
                gate, factorStore, ticketStore, sessionService, loginService, providerOf(new StubAccountProvider()),
                null, policyProvider);
        gate.required = true;

        assertThat(guarded.required(LoginType.TENANT_USER, USER_ID, "alice")).isFalse();
        assertThat(gate.lastRequiredUser).isNull();
    }

    @Test
    void shouldRejectAnInvalidActivationCode() {
        AuthMfaService.MfaEnrollView result = service.enroll(LoginType.TENANT_USER, USER_ID);

        assertThatThrownBy(() -> service.activate(LoginType.TENANT_USER, USER_ID, wrongCode(result.secret())))
                .isInstanceOf(BizException.class)
                .hasMessage("动态码错误");

        assertThat(factorStore.raw(USER_ID).getFailCount()).isEqualTo(1);
        assertThat(service.status(LoginType.TENANT_USER, USER_ID).activated()).isFalse();
    }

    @Test
    void shouldLockTheFactorAfterTooManyFailures() {
        properties.setMaxFailCount(3);
        AuthMfaService.MfaEnrollView result = service.enroll(LoginType.TENANT_USER, USER_ID);
        String valid = code(result.secret());
        service.activate(LoginType.TENANT_USER, USER_ID, valid);
        String wrong = wrongCode(result.secret());

        assertThatThrownBy(() -> service.disable(LoginType.TENANT_USER, USER_ID, wrong)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.disable(LoginType.TENANT_USER, USER_ID, wrong)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.disable(LoginType.TENANT_USER, USER_ID, wrong)).isInstanceOf(BizException.class);

        AuthMfaService.MfaStatusView status = service.status(LoginType.TENANT_USER, USER_ID);
        assertThat(status.locked()).isTrue();
        assertThat(status.lockedUntil()).isNotNull();
        assertThat(factorStore.raw(USER_ID).getFailCount()).isZero();
    }

    @Test
    void shouldUnlockAfterTheLockWindow() {
        properties.setMaxFailCount(2);
        AuthMfaService.MfaEnrollView result = service.enroll(LoginType.TENANT_USER, USER_ID);
        String valid = code(result.secret());
        service.activate(LoginType.TENANT_USER, USER_ID, valid);
        String wrong = wrongCode(result.secret());
        assertThatThrownBy(() -> service.disable(LoginType.TENANT_USER, USER_ID, wrong)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.disable(LoginType.TENANT_USER, USER_ID, wrong)).isInstanceOf(BizException.class);
        assertThat(service.status(LoginType.TENANT_USER, USER_ID).locked()).isTrue();

        factorStore.lock(USER_ID, LocalDateTime.now().minusMinutes(1));

        assertThat(service.status(LoginType.TENANT_USER, USER_ID).locked()).isFalse();
        service.disable(LoginType.TENANT_USER, USER_ID, code(result.secret()));
        assertThat(service.status(LoginType.TENANT_USER, USER_ID).enrolled()).isFalse();
    }

    @Test
    void shouldConsumeRecoveryCodesOnlyOnce() {
        AuthMfaService.MfaEnrollView result = service.enroll(LoginType.TENANT_USER, USER_ID);
        String valid = code(result.secret());
        service.activate(LoginType.TENANT_USER, USER_ID, valid);

        List<String> codes = service.regenerateRecoveryCodes(LoginType.TENANT_USER, USER_ID, valid);

        assertThat(codes).hasSize(properties.getRecoveryCodeCount());
        assertThat(factorStore.raw(USER_ID).getRecoveryCodes()).doesNotContain(codes.get(0));
        assertThat(service.status(LoginType.TENANT_USER, USER_ID).recoveryCodesRemaining())
                .isEqualTo(codes.size());
    }

    @Test
    void shouldRequireAValidCodeToRegenerate() {
        AuthMfaService.MfaEnrollView result = service.enroll(LoginType.TENANT_USER, USER_ID);
        String valid = code(result.secret());
        service.activate(LoginType.TENANT_USER, USER_ID, valid);

        assertThatThrownBy(() -> service.regenerateRecoveryCodes(LoginType.TENANT_USER, USER_ID,
                wrongCode(result.secret())))
                .isInstanceOf(BizException.class)
                .hasMessage("动态码错误");
        assertThat(service.status(LoginType.TENANT_USER, USER_ID).activated()).isTrue();
    }

    @Test
    void shouldRejectEnrollingAnAlreadyActivatedFactor() {
        service.enroll(LoginType.TENANT_USER, USER_ID);
        service.activate(LoginType.TENANT_USER, USER_ID, code(factorStore.raw(USER_ID).getSecretCipher()));

        assertThatThrownBy(() -> service.enroll(LoginType.TENANT_USER, USER_ID))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("二次验证已启用");
    }

    @Test
    void shouldReplaceAnUnactivatedEnrollment() {
        AuthMfaService.MfaEnrollView first = service.enroll(LoginType.TENANT_USER, USER_ID);
        AuthMfaService.MfaEnrollView second = service.enroll(LoginType.TENANT_USER, USER_ID);

        assertThat(second.secret()).isNotEqualTo(first.secret());
        assertThat(factorStore.count()).isEqualTo(1);
        assertThat(service.status(LoginType.TENANT_USER, USER_ID).activated()).isFalse();
    }

    @Test
    void shouldReportTheDisabledStatusForUnknownUsers() {
        assertThat(service.status(LoginType.TENANT_USER, 99L).enrolled()).isFalse();

        assertThatThrownBy(() -> service.activate(LoginType.TENANT_USER, 99L, "123456"))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.disable(LoginType.TENANT_USER, 99L, "123456"))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.regenerateRecoveryCodes(LoginType.TENANT_USER, 99L, "123456"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void shouldRejectUnsupportedAccountSystems() {
        assertThat(service.appliesTo(LoginType.APP)).isFalse();
        assertThat(service.appliesTo(LoginType.SERVICE)).isFalse();
        assertThat(service.appliesTo(LoginType.GUEST)).isFalse();
        assertThat(service.appliesTo(LoginType.PLATFORM_ADMIN)).isTrue();
        assertThat(service.appliesTo(LoginType.PLATFORM_USER)).isTrue();
        assertThat(service.appliesTo(LoginType.TENANT_ADMIN)).isTrue();
        assertThat(service.appliesTo(LoginType.TENANT_USER)).isTrue();

        assertThatThrownBy(() -> service.enroll(LoginType.APP, USER_ID))
                .isInstanceOf(BizException.class)
                .hasMessage("该账号体系不支持二次验证");
        assertThatThrownBy(() -> service.status(LoginType.GUEST, USER_ID))
                .isInstanceOf(BizException.class)
                .hasMessage("该账号体系不支持二次验证");
    }

    @Test
    void shouldDelegateRequiredToTheLibraryGate() {
        gate.required = true;

        assertThat(service.required(LoginType.TENANT_USER, USER_ID, "alice")).isTrue();
        assertThat(gate.lastRequiredUser.userId()).isEqualTo(USER_ID);
        assertThat(gate.lastRequiredUser.username()).isEqualTo("alice");
        assertThat(gate.lastRequiredUser.loginType()).isEqualTo(LoginType.TENANT_USER);

        gate.required = false;
        assertThat(service.required(LoginType.TENANT_USER, null, "alice")).isFalse();
    }

    @Test
    void shouldKeepLoginContextBesideTheLibraryChallenge() {
        String challengeId = service.challenge(LoginType.TENANT_USER, USER_ID, "alice", "PC", "device-1", true);

        assertThat(challengeId).hasSize(32);
        assertThat(gate.challenges).containsKey(challengeId);
        Optional<MfaLoginTicket> ticket = ticketStore.find(challengeId);
        assertThat(ticket).isPresent();
        assertThat(ticket.get().getUserId()).isEqualTo(USER_ID);
        assertThat(ticket.get().getLoginType()).isEqualTo("TENANT_USER");
        assertThat(ticket.get().getDeviceType()).isEqualTo("PC");
        assertThat(ticket.get().getDeviceId()).isEqualTo("device-1");
        assertThat(ticket.get().isRemember()).isTrue();
        assertThat(service.method()).isEqualTo("TOTP");
    }

    @Test
    void shouldEstablishTheSessionAfterAVerifiedChallenge() {
        when(sessionService.establish(any(), any(), any(), any())).thenReturn(new LoginResult("satoken",
                "token-1", USER_ID, "TENANT_USER", 3600L, false));
        when(loginService.completeLogin(any(), any(), anyBoolean())).thenReturn(
                AuthLoginService.AuthLoginView.of(new LoginResult("satoken", "token-1", USER_ID, "TENANT_USER",
                        3600L, false), false, 3600L));

        String challengeId = service.challenge(LoginType.TENANT_USER, USER_ID, "alice", "PC", "device-1", false);
        AuthLoginService.AuthLoginView view = service.verifySecondFactor(challengeId, code(currentSecret()));

        assertThat(view.secondFactorRequired()).isFalse();
        assertThat(view.tokenValue()).isEqualTo("token-1");
        ArgumentCaptor<LoginType> type = ArgumentCaptor.forClass(LoginType.class);
        org.mockito.Mockito.verify(sessionService).establish(type.capture(), eq(USER_ID), eq("PC"), eq("device-1"));
        assertThat(type.getValue()).isEqualTo(LoginType.TENANT_USER);
        org.mockito.Mockito.verify(loginService).completeLogin(eq(LoginType.TENANT_USER), any(), eq(false));
        assertThat(ticketStore.find(challengeId)).isEmpty();
        assertThat(gate.challenges).doesNotContainKey(challengeId);
    }

    @Test
    void shouldRejectAVerificationWithABlankInput() {
        assertThatThrownBy(() -> service.verifySecondFactor(" ", "123456"))
                .isInstanceOf(BizException.class)
                .hasMessage("挑战标识与动态码不能为空");
        assertThatThrownBy(() -> service.verifySecondFactor("abc", " "))
                .isInstanceOf(BizException.class)
                .hasMessage("挑战标识与动态码不能为空");
    }

    @Test
    void shouldRejectAVerificationWithoutALoginTicket() {
        gate.challenges.put("known", UserContext.UserInfo.of(USER_ID, "alice", LoginType.TENANT_USER));
        gate.acceptAnyChallenge = true;

        assertThatThrownBy(() -> service.verifySecondFactor("known", code(currentSecret())))
                .isInstanceOf(BizException.class)
                .hasMessage("二次验证已失效，请重新登录");
    }

    @Test
    void shouldExposeTheFactorThroughThePort() {
        service.enroll(LoginType.TENANT_USER, USER_ID);

        MfaFactorSnapshot enrolled = service.factorOf(USER_ID);
        assertThat(enrolled).isNotNull();
        assertThat(enrolled.userId()).isEqualTo(USER_ID);
        assertThat(enrolled.factorType()).isEqualTo("TOTP");
        assertThat(enrolled.activated()).isFalse();

        factorStore.updateActivation(USER_ID, "TOTP", true);
        factorStore.updateFailure(USER_ID, "TOTP", 3, LocalDateTime.now().plusMinutes(5));
        MfaFactorSnapshot failed = service.factorOf(USER_ID);
        assertThat(failed.activated()).isTrue();
        assertThat(failed.failCount()).isEqualTo(3);
        assertThat(failed.lockedUntil()).isNotNull();

        LocalDateTime usedAt = LocalDateTime.now();
        factorStore.updateSuccess(USER_ID, "TOTP", usedAt);
        assertThat(service.factorOf(USER_ID).lastUsedAt()).isEqualTo(usedAt);

        factorStore.updateRecoveryCodes(USER_ID, "TOTP", "hash-1");
        assertThat(service.factorOf(USER_ID).recoveryCodes()).isEqualTo("hash-1");

        assertThat(factorStore.delete(USER_ID, "TOTP")).isTrue();
        assertThat(service.factorOf(USER_ID)).isNull();
        assertThat(factorStore.find(USER_ID, null)).isNull();
    }

    @Test
    void shouldInsertAFactorThroughThePort() {
        Long id = factorStore.insert(new MfaFactorSnapshot(null, 2L, "TOTP", "cipher", "key-1",
                Boolean.FALSE, null, null, 0, null, null));

        assertThat(id).isNotNull();
        MfaFactorSnapshot stored = factorStore.find(2L, "TOTP");
        assertThat(stored.secretCipher()).isEqualTo("cipher");
        assertThat(stored.activated()).isFalse();
    }

    private String currentSecret() {
        MfaFactorRecord record = factorStore.raw(USER_ID);
        return record == null ? totp.generateSecretKey() : record.getSecretCipher();
    }

    private String code(String secret) {
        return totp.generate(secret);
    }

    private String wrongCode(String secret) {
        String valid = code(secret);
        char last = valid.charAt(valid.length() - 1);
        return valid.substring(0, valid.length() - 1) + (last == '0' ? '1' : '0');
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<AccountAuthProvider> providerOf(AccountAuthProvider provider) {
        ObjectProvider<AccountAuthProvider> mock = mock(ObjectProvider.class);
        when(mock.getIfAvailable()).thenReturn(provider);
        return mock;
    }

    private static final class StubAccountProvider implements AccountAuthProvider {

        @Override
        public PasswordAccount findAccount(String username, LoginType type) {
            return null;
        }

        @Override
        public PasswordAccount findAccountByUserId(Long userId, LoginType type) {
            return new PasswordAccount(userId, "alice", "hash", true, null, false);
        }
    }

    private static final class StubGate implements SecondFactorGate {

        private final Map<String, UserContext.UserInfo> challenges = new LinkedHashMap<>();

        private boolean required;

        private boolean acceptAnyChallenge;

        private UserContext.UserInfo lastRequiredUser;

        @Override
        public boolean required(UserContext.UserInfo user) {
            lastRequiredUser = user;
            return required;
        }

        @Override
        public String challenge(UserContext.UserInfo user) {
            String challengeId = java.util.UUID.randomUUID().toString().replace("-", "");
            challenges.put(challengeId, user);
            return challengeId;
        }

        @Override
        public UserContext.UserInfo verify(String challengeId, String code) {
            UserContext.UserInfo user = acceptAnyChallenge
                    ? UserContext.UserInfo.of(1L, "alice", LoginType.TENANT_USER)
                    : challenges.remove(challengeId);
            if (user == null) {
                throw new BizException("二次验证已失效，请重新登录");
            }
            return user;
        }

        @Override
        public String method() {
            return "TOTP";
        }

        @Override
        public Duration challengeTtl() {
            return Duration.ofMinutes(5);
        }
    }

    private static final class InMemoryMfaFactorStore implements MfaFactorStore {

        private final Map<String, MfaFactorRecord> factors = new LinkedHashMap<>();

        private long sequence;

        @Override
        public MfaFactorRecord findRecord(Long userId, String factorType) {
            MfaFactorRecord record = factors.get(key(userId, factorType));
            return record == null ? null : copy(record);
        }

        @Override
        public MfaFactorRecord saveRecord(MfaFactorRecord record) {
            if (record.getId() == null) {
                record.setId(++sequence);
            }
            factors.put(key(record.getUserId(), record.getFactorType()), copy(record));
            return record;
        }

        @Override
        public MfaFactorSnapshot find(Long userId, String factorType) {
            MfaFactorRecord record = findRecord(userId, factorType);
            return record == null ? null : new MfaFactorSnapshot(record.getId(), record.getUserId(),
                    record.getFactorType(), record.getSecretCipher(), record.getSecretKeyId(), record.getActivated(),
                    record.getRecoveryCodes(), record.getLastUsedAt(), record.getFailCount(), record.getLockedUntil(),
                    record.getTenantId());
        }

        @Override
        public Long insert(MfaFactorSnapshot factor) {
            MfaFactorRecord record = new MfaFactorRecord();
            record.setUserId(factor.userId());
            record.setFactorType(factor.factorType());
            record.setSecretCipher(factor.secretCipher());
            record.setSecretKeyId(factor.secretKeyId());
            record.setActivated(factor.activated());
            record.setRecoveryCodes(factor.recoveryCodes());
            record.setLastUsedAt(factor.lastUsedAt());
            record.setFailCount(factor.failCount());
            record.setLockedUntil(factor.lockedUntil());
            record.setTenantId(factor.tenantId());
            saveRecord(record);
            return record.getId();
        }

        @Override
        public void updateActivation(Long userId, String factorType, boolean activated) {
            MfaFactorRecord record = factors.get(key(userId, factorType));
            if (record != null) {
                record.setActivated(activated);
            }
        }

        @Override
        public void updateFailure(Long userId, String factorType, int failCount, LocalDateTime lockedUntil) {
            MfaFactorRecord record = factors.get(key(userId, factorType));
            if (record != null) {
                record.setFailCount(failCount);
                record.setLockedUntil(lockedUntil);
            }
        }

        @Override
        public void updateSuccess(Long userId, String factorType, LocalDateTime lastUsedAt) {
            MfaFactorRecord record = factors.get(key(userId, factorType));
            if (record != null) {
                record.setFailCount(0);
                record.setLockedUntil(null);
                record.setLastUsedAt(lastUsedAt);
            }
        }

        @Override
        public void updateRecoveryCodes(Long userId, String factorType, String recoveryCodes) {
            MfaFactorRecord record = factors.get(key(userId, factorType));
            if (record != null) {
                record.setRecoveryCodes(recoveryCodes);
            }
        }

        @Override
        public boolean delete(Long userId, String factorType) {
            return factors.remove(key(userId, factorType)) != null;
        }

        MfaFactorRecord raw(Long userId) {
            return factors.get(key(userId, "TOTP"));
        }

        int count() {
            return factors.size();
        }

        void lock(Long userId, LocalDateTime lockedUntil) {
            raw(userId).setLockedUntil(lockedUntil);
        }

        private static String key(Long userId, String factorType) {
            return userId + ":" + factorType;
        }

        private static MfaFactorRecord copy(MfaFactorRecord source) {
            MfaFactorRecord target = new MfaFactorRecord();
            target.setId(source.getId());
            target.setTenantId(source.getTenantId());
            target.setUserId(source.getUserId());
            target.setLoginType(source.getLoginType());
            target.setUsername(source.getUsername());
            target.setFactorType(source.getFactorType());
            target.setSecretCipher(source.getSecretCipher());
            target.setSecretKeyId(source.getSecretKeyId());
            target.setActivated(source.getActivated());
            target.setRecoveryCodes(source.getRecoveryCodes());
            target.setLastUsedAt(source.getLastUsedAt());
            target.setFailCount(source.getFailCount());
            target.setLockedUntil(source.getLockedUntil());
            return target;
        }
    }
}
