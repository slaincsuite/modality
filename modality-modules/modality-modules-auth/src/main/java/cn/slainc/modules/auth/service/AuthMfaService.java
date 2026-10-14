package cn.slainc.modules.auth.service;

import cn.slainc.core.context.TenantContext;
import cn.slainc.core.context.UserContext;
import cn.slainc.core.enums.LoginType;
import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;
import cn.slainc.mfa.authentication.SecondFactorGate;
import cn.slainc.mfa.config.MfaConfigReader;
import cn.slainc.mfa.recovery.RecoveryCodeGenerator;
import cn.slainc.mfa.spi.MfaFactorSnapshot;
import cn.slainc.mfa.support.MfaSecretCodec;
import cn.slainc.mfa.totp.TotpEngine;
import cn.slainc.modules.auth.mfa.AuthMfaUserLoader;
import cn.slainc.modules.auth.mfa.MfaFactorRecord;
import cn.slainc.modules.auth.mfa.MfaLoginTicket;
import cn.slainc.modules.auth.store.AuthTicketStore;
import cn.slainc.modules.auth.store.MfaFactorStore;
import cn.slainc.security.login.LoginResult;
import cn.slainc.security.spi.AccountAuthProvider;
import cn.slainc.security.spi.PasswordAccount;
import cn.slainc.security.spi.SecurityPermissionProvider;
import cn.slainc.utils.string.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class AuthMfaService implements AuthSecondFactorGate {

    public static final String FACTOR_TYPE_TOTP = AuthMfaUserLoader.FACTOR_TYPE_TOTP;

    private static final Logger log = LoggerFactory.getLogger(AuthMfaService.class);

    private static final String RECOVERY_SEPARATOR = ",";

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final MfaConfigReader config;

    private final TotpEngine totp;

    private final RecoveryCodeGenerator recoveryCodeGenerator;

    private final MfaSecretCodec secretCodec;

    private final SecondFactorGate gate;

    private final MfaFactorStore factorStore;

    private final AuthTicketStore<MfaLoginTicket> ticketStore;

    private final AuthSessionService sessionService;

    private final AuthLoginService loginService;

    private final ObjectProvider<AccountAuthProvider> authProvider;

    private final ObjectProvider<SecurityPermissionProvider> permissionProvider;

    public AuthMfaService(MfaConfigReader config, TotpEngine totp, RecoveryCodeGenerator recoveryCodeGenerator,
                          MfaSecretCodec secretCodec, SecondFactorGate gate, MfaFactorStore factorStore,
                          AuthTicketStore<MfaLoginTicket> ticketStore, AuthSessionService sessionService,
                          AuthLoginService loginService, ObjectProvider<AccountAuthProvider> authProvider,
                          ObjectProvider<SecurityPermissionProvider> permissionProvider) {
        this.config = config;
        this.totp = totp;
        this.recoveryCodeGenerator = recoveryCodeGenerator;
        this.secretCodec = secretCodec;
        this.gate = gate;
        this.factorStore = factorStore;
        this.ticketStore = ticketStore;
        this.sessionService = sessionService;
        this.loginService = loginService;
        this.authProvider = authProvider;
        this.permissionProvider = permissionProvider;
    }

    public record MfaStatusView(boolean enrolled, boolean activated, String method, int recoveryCodesRemaining,
                                LocalDateTime lastUsedAt, boolean locked, LocalDateTime lockedUntil) {

        public static MfaStatusView disabled() {
            return new MfaStatusView(false, false, FACTOR_TYPE_TOTP, 0, null, false, null);
        }
    }

    public record MfaEnrollView(String secret, String otpauthUrl, String issuer, int digits, int periodSeconds) {
    }

    @Override
    public boolean appliesTo(LoginType type) {
        return type != null && AuthMfaUserLoader.USER_LOGIN_TYPES.contains(type);
    }

    @Override
    public boolean required(LoginType type, Long userId, String username) {
        if (userId == null) {
            return false;
        }
        return gate.required(userOf(type, userId, username));
    }

    @Override
    public String challenge(LoginType type, Long userId, String username, String deviceType, String deviceId,
                            boolean remember) {
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "登录状态异常，无法发起二次验证");
        }
        String challengeId = gate.challenge(userOf(type, userId, username));
        long ttlSeconds = Math.max(1, challengeTtl().getSeconds());
        ticketStore.save(MfaLoginTicket.of(challengeId, userId, username, type.value(), deviceType, deviceId,
                remember, ttlSeconds), ttlSeconds);
        log.info("用户 {} 登录需要二次验证，已签发挑战 {}", userId, challengeId);
        return challengeId;
    }

    @Override
    public String method() {
        return gate.method();
    }

    public AuthLoginService.AuthLoginView verifySecondFactor(String challengeId, String code) {
        if (StringUtils.isBlank(challengeId) || StringUtils.isBlank(code)) {
            throw new BizException(ResultCode.PARAM_MISSING, "挑战标识与动态码不能为空");
        }
        UserContext.UserInfo user = gate.verify(challengeId, code);
        MfaLoginTicket ticket = ticketStore.consume(challengeId).orElse(null);
        if (ticket == null || ticket.expired(System.currentTimeMillis())) {
            throw new BizException(ResultCode.UNAUTHORIZED, "二次验证已失效，请重新登录");
        }
        LoginType type = resolveLoginType(ticket, user);
        LoginResult result = sessionService.establish(type, user.userId(), ticket.getDeviceType(),
                ticket.getDeviceId());
        log.info("用户 {} 通过二次验证完成登录", user.userId());
        return loginService.completeLogin(type, result, ticket.isRemember());
    }

    public MfaEnrollView enroll(LoginType type, Long userId) {
        requireUserType(type, userId);
        String username = usernameOf(type, userId);
        MfaFactorRecord existing = factorStore.findRecord(userId, FACTOR_TYPE_TOTP);
        if (existing != null && Boolean.TRUE.equals(existing.getActivated())) {
            throw new BizException(ResultCode.DATA_STATE_INVALID, "二次验证已启用，如需重置请先关闭");
        }
        String secret = totp.generateSecretKey();
        MfaFactorRecord record = existing == null ? new MfaFactorRecord() : existing;
        record.setUserId(userId);
        record.setLoginType(type.value());
        record.setUsername(username);
        record.setFactorType(FACTOR_TYPE_TOTP);
        record.setSecretCipher(secretCodec.encrypt(secret));
        record.setSecretKeyId(secretCodec.keyId());
        record.setActivated(Boolean.FALSE);
        record.setRecoveryCodes(null);
        record.setLastUsedAt(null);
        record.setFailCount(0);
        record.setLockedUntil(null);
        record.setTenantId(TenantContext.get());
        factorStore.saveRecord(record);
        String issuer = issuer();
        String otpauthUrl = totp.otpAuthUri(issuer, account(username, userId), secret);
        log.info("用户 {} 发起二次验证绑定", userId);
        return new MfaEnrollView(secret, otpauthUrl, issuer, totp.digits(), totp.periodSeconds());
    }

    public void activate(LoginType type, Long userId, String code) {
        requireUserType(type, userId);
        MfaFactorRecord factor = requireFactor(userId);
        if (Boolean.TRUE.equals(factor.getActivated())) {
            throw new BizException(ResultCode.DATA_STATE_INVALID, "二次验证已激活");
        }
        requireVerified(factor, code);
        factor.setActivated(Boolean.TRUE);
        factor.setFailCount(0);
        factor.setLockedUntil(null);
        factor.setLastUsedAt(LocalDateTime.now());
        factorStore.saveRecord(factor);
        log.info("用户 {} 激活二次验证", userId);
    }

    public void disable(LoginType type, Long userId, String code) {
        requireUserType(type, userId);
        MfaFactorRecord factor = requireActivatedFactor(userId);
        requireVerified(factor, code);
        factorStore.delete(userId, FACTOR_TYPE_TOTP);
        log.info("用户 {} 关闭二次验证", userId);
    }

    public MfaStatusView status(LoginType type, Long userId) {
        requireUserType(type, userId);
        MfaFactorRecord factor = userId == null ? null : factorStore.findRecord(userId, FACTOR_TYPE_TOTP);
        if (factor == null) {
            return MfaStatusView.disabled();
        }
        boolean locked = locked(factor);
        return new MfaStatusView(true, Boolean.TRUE.equals(factor.getActivated()), FACTOR_TYPE_TOTP,
                parseRecoveryCodes(factor).size(), factor.getLastUsedAt(), locked,
                locked ? factor.getLockedUntil() : null);
    }

    public List<String> regenerateRecoveryCodes(LoginType type, Long userId, String code) {
        requireUserType(type, userId);
        MfaFactorRecord factor = requireActivatedFactor(userId);
        requireVerified(factor, code);
        List<String> codes = recoveryCodeGenerator.generate(config.recoveryCodeCount());
        factor.setRecoveryCodes(joinRecoveryCodes(codes.stream().map(recoveryCodeGenerator::hash).toList()));
        factor.setFailCount(0);
        factor.setLockedUntil(null);
        factorStore.saveRecord(factor);
        log.info("用户 {} 重新生成 {} 枚恢复码", userId, codes.size());
        return codes;
    }

    public MfaFactorSnapshot factorOf(Long userId) {
        return factorStore.find(userId, FACTOR_TYPE_TOTP);
    }

    public Duration challengeTtl() {
        return gate.challengeTtl();
    }

    private UserContext.UserInfo userOf(LoginType type, Long userId, String username) {
        return new UserContext.UserInfo(userId, username, type, rolesOf(type, userId), java.util.Set.of(), false);
    }

    private java.util.Set<String> rolesOf(LoginType type, Long userId) {
        SecurityPermissionProvider provider = permissionProvider == null ? null : permissionProvider.getIfAvailable();
        if (provider == null || userId == null) {
            return java.util.Set.of();
        }
        try {
            return java.util.Set.copyOf(provider.getRoles(userId, type));
        } catch (RuntimeException e) {
            return java.util.Set.of();
        }
    }

    private LoginType resolveLoginType(MfaLoginTicket ticket, UserContext.UserInfo user) {
        LoginType ticketType = LoginType.of(ticket.getLoginType());
        return ticketType != null ? ticketType : user.loginType();
    }

    private String usernameOf(LoginType type, Long userId) {
        AccountAuthProvider provider = authProvider == null ? null : authProvider.getIfAvailable();
        PasswordAccount account = provider == null ? null : provider.findAccountByUserId(userId, type);
        return account == null ? null : account.username();
    }

    private void requireUserType(LoginType type, Long userId) {
        if (!appliesTo(type)) {
            throw new BizException(ResultCode.FORBIDDEN, "该账号体系不支持二次验证");
        }
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "登录状态已失效，请重新登录");
        }
    }

    private void requireVerified(MfaFactorRecord factor, String code) {
        if (locked(factor)) {
            throw new BizException(ResultCode.FORBIDDEN, lockMessage(factor));
        }
        String secret = secretKey(factor);
        if (secret == null) {
            throw new BizException(ResultCode.DATA_STATE_INVALID, "二次验证密钥不可用，请重新绑定");
        }
        if (!totp.verify(secret, code)) {
            recordFailure(factor);
            throw new BizException("动态码错误");
        }
        applySuccess(factor);
    }

    private String secretKey(MfaFactorRecord factor) {
        String plain;
        try {
            plain = secretCodec.decrypt(factor.getSecretCipher());
        } catch (RuntimeException e) {
            log.warn("解密二次验证密钥失败 userId={}：{}", factor.getUserId(), e.getMessage());
            return null;
        }
        if (StringUtils.isBlank(plain)) {
            return null;
        }
        return plain.trim().replace(" ", "").replace("=", "");
    }

    private void recordFailure(MfaFactorRecord factor) {
        int failures = (factor.getFailCount() == null ? 0 : factor.getFailCount()) + 1;
        factor.setFailCount(failures);
        int maxFailCount = config.maxFailCount();
        if (maxFailCount > 0 && failures >= maxFailCount) {
            factor.setFailCount(0);
            factor.setLockedUntil(LocalDateTime.now().plus(lockDuration()));
            log.warn("用户 {} 连续 {} 次二次验证失败，已锁定", factor.getUserId(), failures);
        }
        factorStore.saveRecord(factor);
    }

    private void applySuccess(MfaFactorRecord factor) {
        factor.setFailCount(0);
        factor.setLockedUntil(null);
        factor.setLastUsedAt(LocalDateTime.now());
    }

    private boolean locked(MfaFactorRecord factor) {
        LocalDateTime lockedUntil = factor.getLockedUntil();
        return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now());
    }

    private String lockMessage(MfaFactorRecord factor) {
        LocalDateTime lockedUntil = factor.getLockedUntil();
        return lockedUntil == null ? "二次验证已锁定，请稍后重试或使用恢复码"
                : "二次验证已锁定，请于 " + TIME_FORMATTER.format(lockedUntil) + " 后重试或使用恢复码";
    }

    private MfaFactorRecord requireFactor(Long userId) {
        MfaFactorRecord factor = factorStore.findRecord(userId, FACTOR_TYPE_TOTP);
        if (factor == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "尚未开启二次验证");
        }
        return factor;
    }

    private MfaFactorRecord requireActivatedFactor(Long userId) {
        MfaFactorRecord factor = requireFactor(userId);
        if (!Boolean.TRUE.equals(factor.getActivated())) {
            throw new BizException(ResultCode.DATA_STATE_INVALID, "二次验证尚未激活");
        }
        return factor;
    }

    private List<String> parseRecoveryCodes(MfaFactorRecord factor) {
        String stored = factor.getRecoveryCodes();
        if (StringUtils.isBlank(stored)) {
            return List.of();
        }
        List<String> hashes = new ArrayList<>();
        for (String item : stored.split(RECOVERY_SEPARATOR)) {
            String hash = item.trim();
            if (!hash.isEmpty()) {
                hashes.add(hash);
            }
        }
        return hashes;
    }

    private String joinRecoveryCodes(List<String> hashes) {
        return hashes.isEmpty() ? null : hashes.stream().collect(Collectors.joining(RECOVERY_SEPARATOR));
    }

    private String issuer() {
        return StringUtils.defaultIfBlank(config.issuer(), "modality");
    }

    private String account(String username, Long userId) {
        return StringUtils.isBlank(username) ? "user-" + userId : username;
    }

    private Duration lockDuration() {
        Duration duration = config.lockDuration();
        return duration == null || duration.isZero() || duration.isNegative() ? Duration.ofMinutes(10) : duration;
    }
}
