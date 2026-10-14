package cn.slainc.mfa.support;

import cn.slainc.core.context.UserContext;
import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;
import cn.slainc.mfa.api.MfaChallengeStore;
import cn.slainc.mfa.authentication.SecondFactorGate;
import cn.slainc.mfa.challenge.MfaChallenge;
import cn.slainc.mfa.config.MfaConfigReader;
import cn.slainc.mfa.recovery.RecoveryCodeGenerator;
import cn.slainc.mfa.spi.MfaFactorPort;
import cn.slainc.mfa.spi.MfaFactorSnapshot;
import cn.slainc.mfa.spi.MfaUserLoader;
import cn.slainc.mfa.totp.TotpEngine;
import cn.slainc.utils.string.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class MfaSecondFactorGate implements SecondFactorGate {

    public static final String METHOD_TOTP = "TOTP";

    private static final Logger log = LoggerFactory.getLogger(MfaSecondFactorGate.class);

    private static final String RECOVERY_SEPARATOR = ",";

    private final ObjectProvider<MfaFactorPort> factorPortProvider;

    private final MfaChallengeStore challengeStore;

    private final MfaConfigReader config;

    private final MfaSecretCodec secretCodec;

    private final TotpEngine totp;

    private final RecoveryCodeGenerator recoveryCodeGenerator;

    private final ObjectProvider<MfaUserLoader> userLoader;

    public MfaSecondFactorGate(ObjectProvider<MfaFactorPort> factorPortProvider, MfaChallengeStore challengeStore,
                               MfaConfigReader config, MfaSecretCodec secretCodec, TotpEngine totp,
                               RecoveryCodeGenerator recoveryCodeGenerator,
                               ObjectProvider<MfaUserLoader> userLoader) {
        this.factorPortProvider = factorPortProvider;
        this.challengeStore = challengeStore;
        this.config = config;
        this.secretCodec = secretCodec;
        this.totp = totp;
        this.recoveryCodeGenerator = recoveryCodeGenerator;
        this.userLoader = userLoader;
    }

    @Override
    public boolean required(UserContext.UserInfo user) {
        if (user == null || user.userId() == null) {
            return false;
        }
        if (requiredByRole(user)) {
            return true;
        }
        try {
            MfaFactorSnapshot factor = find(user.userId());
            return factor != null && Boolean.TRUE.equals(factor.activated());
        } catch (RuntimeException e) {
            log.warn("评估二次验证状态失败 userId={} 原因：{}", user.userId(), e.getMessage());
            return false;
        }
    }

    private boolean requiredByRole(UserContext.UserInfo user) {
        List<String> roles = config.requireForRoles();
        if (roles == null || roles.isEmpty()) {
            return false;
        }
        for (String role : user.roles()) {
            if (roles.contains(role)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String challenge(UserContext.UserInfo user) {
        if (user == null || user.userId() == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "登录状态异常，无法发起二次验证");
        }
        Duration ttl = challengeTtl();
        String challengeId = UUID.randomUUID().toString().replace("-", "");
        challengeStore.save(MfaChallenge.of(challengeId, user.userId(), user.username(), method(), ttl), ttl);
        return challengeId;
    }

    @Override
    public UserContext.UserInfo verify(String challengeId, String code) {
        if (StringUtils.isBlank(challengeId)) {
            throw new BizException(ResultCode.PARAM_MISSING, "挑战标识不能为空");
        }
        MfaChallenge challenge = challengeStore.consume(challengeId);
        if (challenge == null || challenge.userId() == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "二次验证已失效，请重新登录");
        }
        Long userId = challenge.userId();
        MfaFactorPort factorPort = requireFactorPort();
        MfaFactorSnapshot factor = factorPort.find(userId, METHOD_TOTP);
        if (factor == null || !Boolean.TRUE.equals(factor.activated())) {
            throw new BizException(ResultCode.FORBIDDEN, "该账号尚未完成二次验证绑定，请联系管理员");
        }
        if (!verifyCode(factorPort, factor, code) && !verifyRecoveryCode(factorPort, factor, code)) {
            MfaFactorSnapshot refreshed = factorPort.find(userId, METHOD_TOTP);
            if (refreshed != null && locked(refreshed)) {
                throw new BizException(ResultCode.FORBIDDEN, "二次验证已锁定，请稍后重试或使用恢复码");
            }
            throw new BizException(ResultCode.UNAUTHORIZED, "动态码或恢复码错误");
        }
        return loadUser(userId);
    }

    private boolean verifyCode(MfaFactorPort factorPort, MfaFactorSnapshot factor, String code) {
        if (locked(factor)) {
            log.warn("二次验证因子处于锁定状态，已拒绝校验 userId={}", factor.userId());
            return false;
        }
        if (!matches(factor, code)) {
            recordFailure(factorPort, factor);
            return false;
        }
        recordSuccess(factorPort, factor);
        return true;
    }

    private boolean verifyRecoveryCode(MfaFactorPort factorPort, MfaFactorSnapshot factor, String code) {
        if (!Boolean.TRUE.equals(factor.activated()) || StringUtils.isBlank(code)) {
            return false;
        }
        List<String> hashes = parseRecoveryCodes(factor);
        String matched = null;
        for (String hash : hashes) {
            if (recoveryCodeGenerator.matches(code, hash)) {
                matched = hash;
                break;
            }
        }
        if (matched == null) {
            return false;
        }
        List<String> remaining = new ArrayList<>(hashes);
        remaining.remove(matched);
        factorPort.updateRecoveryCodes(factor.userId(), factor.factorType(), joinRecoveryCodes(remaining));
        factorPort.updateSuccess(factor.userId(), factor.factorType(), LocalDateTime.now());
        log.info("已消耗一枚恢复码 userId={}，剩余 {} 枚", factor.userId(), remaining.size());
        return true;
    }

    private boolean matches(MfaFactorSnapshot factor, String code) {
        if (StringUtils.isBlank(code)) {
            return false;
        }
        String secret = secretKey(factor);
        return secret != null && totp.verify(secret, code);
    }

    private String secretKey(MfaFactorSnapshot factor) {
        String plain;
        try {
            plain = secretCodec.decrypt(factor.secretCipher());
        } catch (RuntimeException e) {
            log.warn("解密二次验证密钥失败 userId={} 原因：{}", factor.userId(), e.getMessage());
            return null;
        }
        if (StringUtils.isBlank(plain)) {
            log.warn("二次验证因子缺少可用密钥 userId={}", factor.userId());
            return null;
        }
        return plain.trim().replace(" ", "").replace("=", "");
    }

    private void recordFailure(MfaFactorPort factorPort, MfaFactorSnapshot factor) {
        int failures = (factor.failCount() == null ? 0 : factor.failCount()) + 1;
        int maxFailCount = config.maxFailCount();
        if (maxFailCount > 0 && failures >= maxFailCount) {
            factorPort.updateFailure(factor.userId(), factor.factorType(), 0, LocalDateTime.now().plus(lockDuration()));
            log.warn("连续 {} 次二次验证失败，已锁定因子 userId={}", failures, factor.userId());
            return;
        }
        factorPort.updateFailure(factor.userId(), factor.factorType(), failures, factor.lockedUntil());
    }

    private void recordSuccess(MfaFactorPort factorPort, MfaFactorSnapshot factor) {
        factorPort.updateSuccess(factor.userId(), factor.factorType(), LocalDateTime.now());
    }

    private boolean locked(MfaFactorSnapshot factor) {
        LocalDateTime lockedUntil = factor.lockedUntil();
        return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now());
    }

    private MfaFactorSnapshot find(Long userId) {
        MfaFactorPort factorPort = factorPortProvider == null ? null : factorPortProvider.getIfAvailable();
        return factorPort == null || userId == null ? null : factorPort.find(userId, METHOD_TOTP);
    }

    private MfaFactorPort requireFactorPort() {
        MfaFactorPort factorPort = factorPortProvider == null ? null : factorPortProvider.getIfAvailable();
        if (factorPort == null) {
            log.warn("modality.mfa：未提供 MfaFactorPort，TOTP 二次验证不可用");
            throw new BizException(ResultCode.FORBIDDEN, "未启用二次验证");
        }
        return factorPort;
    }

    private List<String> parseRecoveryCodes(MfaFactorSnapshot factor) {
        String stored = factor.recoveryCodes();
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

    private UserContext.UserInfo loadUser(Long userId) {
        MfaUserLoader loader = userLoader == null ? null : userLoader.getIfAvailable();
        if (loader == null) {
            throw new BizException(ResultCode.FORBIDDEN, "未提供用户信息加载器，无法完成二次验证");
        }
        UserContext.UserInfo user = loader.loadByUserId(userId);
        if (user == null || user.userId() == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "用户不存在或已停用");
        }
        return user;
    }

    @Override
    public String method() {
        return METHOD_TOTP;
    }

    @Override
    public Duration challengeTtl() {
        Duration ttl = config.challengeTtl();
        return ttl == null || ttl.isZero() || ttl.isNegative() ? MfaChallenge.DEFAULT_TTL : ttl;
    }

    private Duration lockDuration() {
        Duration duration = config.lockDuration();
        return duration == null || duration.isZero() || duration.isNegative() ? Duration.ofMinutes(10) : duration;
    }
}
