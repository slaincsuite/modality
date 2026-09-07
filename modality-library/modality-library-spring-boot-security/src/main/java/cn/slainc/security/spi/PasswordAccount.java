package cn.slainc.security.spi;

import java.time.LocalDateTime;

public record PasswordAccount(Long userId, String username, String passwordHash, boolean enabled,
                              LocalDateTime passwordChangedAt, boolean mustChangePassword) {
}
