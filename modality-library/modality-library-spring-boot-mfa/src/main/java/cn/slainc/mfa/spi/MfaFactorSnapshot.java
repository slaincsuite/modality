package cn.slainc.mfa.spi;

import java.time.LocalDateTime;

public record MfaFactorSnapshot(Long id, Long userId, String factorType, String secretCipher, String secretKeyId,
                                Boolean activated, String recoveryCodes, LocalDateTime lastUsedAt, Integer failCount,
                                LocalDateTime lockedUntil, Long tenantId) {
}
