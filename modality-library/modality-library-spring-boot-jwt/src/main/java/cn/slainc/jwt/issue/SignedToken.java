package cn.slainc.jwt.issue;

import java.time.Instant;

public record SignedToken(String tokenValue, String jti, TokenType tokenType, Instant expiresAt, String kid) {
}
