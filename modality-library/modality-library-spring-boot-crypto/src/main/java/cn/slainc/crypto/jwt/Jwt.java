package cn.slainc.crypto.jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class Jwt {

    private final String token;

    private final Map<String, Object> header;

    private final Map<String, Object> claims;

    private final byte[] signature;

    private final String signingInput;

    public Jwt(String token, Map<String, Object> header, Map<String, Object> claims, byte[] signature, String signingInput) {
        this.token = token;
        this.header = Map.copyOf(header);
        this.claims = Map.copyOf(claims);
        this.signature = signature == null ? new byte[0] : signature.clone();
        this.signingInput = signingInput;
    }

    public String token() {
        return token;
    }

    public Map<String, Object> header() {
        return header;
    }

    public Map<String, Object> claims() {
        return claims;
    }

    public byte[] signature() {
        return signature.clone();
    }

    public String signingInput() {
        return signingInput;
    }

    public String algorithm() {
        return stringClaim("alg") != null ? stringClaim("alg") : string(header.get("alg"));
    }

    public String keyId() {
        return string(header.get("kid"));
    }

    public String type() {
        return string(header.get("typ"));
    }

    public String subject() {
        return string(claims.get("sub"));
    }

    public String issuer() {
        return string(claims.get("iss"));
    }

    public String audience() {
        Object value = claims.get("aud");
        if (value instanceof List<?> list) {
            return list.isEmpty() ? null : string(list.get(0));
        }
        return string(value);
    }

    public Instant issuedAt() {
        return instant("iat");
    }

    public Instant expiresAt() {
        return instant("exp");
    }

    public Instant notBefore() {
        return instant("nbf");
    }

    public String jwtId() {
        return string(claims.get("jti"));
    }

    public boolean hasClaim(String name) {
        return claims.containsKey(name);
    }

    public Object claim(String name) {
        return claims.get(name);
    }

    public String stringClaim(String name) {
        return string(claims.get(name));
    }

    public Long longClaim(String name) {
        Object value = claims.get(name);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? null : Long.valueOf(String.valueOf(value));
    }

    public Integer intClaim(String name) {
        Long value = longClaim(name);
        return value == null ? null : value.intValue();
    }

    public Boolean booleanClaim(String name) {
        Object value = claims.get(name);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value == null ? null : Boolean.valueOf(String.valueOf(value));
    }

    public boolean isExpired() {
        return isExpired(Instant.now());
    }

    public boolean isExpired(Instant instant) {
        Instant expiresAt = expiresAt();
        return expiresAt != null && instant.isAfter(expiresAt);
    }

    public boolean isActive() {
        return isActive(Instant.now());
    }

    public boolean isActive(Instant instant) {
        Instant expiresAt = expiresAt();
        Instant notBefore = notBefore();
        return (expiresAt == null || !instant.isAfter(expiresAt)) && (notBefore == null || !instant.isBefore(notBefore));
    }

    private Instant instant(String name) {
        Long value = longClaim(name);
        return value == null ? null : Instant.ofEpochSecond(value);
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
