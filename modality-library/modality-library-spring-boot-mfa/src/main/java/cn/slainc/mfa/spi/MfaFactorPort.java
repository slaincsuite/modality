package cn.slainc.mfa.spi;

import java.time.LocalDateTime;

public interface MfaFactorPort {

    MfaFactorSnapshot find(Long userId, String factorType);

    Long insert(MfaFactorSnapshot factor);

    void updateActivation(Long userId, String factorType, boolean activated);

    void updateFailure(Long userId, String factorType, int failCount, LocalDateTime lockedUntil);

    void updateSuccess(Long userId, String factorType, LocalDateTime lastUsedAt);

    void updateRecoveryCodes(Long userId, String factorType, String recoveryCodes);

    boolean delete(Long userId, String factorType);
}
