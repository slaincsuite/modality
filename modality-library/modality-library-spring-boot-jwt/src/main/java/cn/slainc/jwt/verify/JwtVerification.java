package cn.slainc.jwt.verify;

import cn.slainc.jwt.issue.TokenType;

import java.util.Map;

public record JwtVerification(boolean valid, String error, Map<String, Object> claims, String kid, TokenType tokenType,
                              String jti) {

    public static JwtVerification ok(Map<String, Object> claims, String kid, TokenType tokenType, String jti) {
        return new JwtVerification(true, null, claims == null ? Map.of() : Map.copyOf(claims), kid, tokenType, jti);
    }

    public static JwtVerification fail(String error) {
        return new JwtVerification(false, error, Map.of(), null, null, null);
    }
}
