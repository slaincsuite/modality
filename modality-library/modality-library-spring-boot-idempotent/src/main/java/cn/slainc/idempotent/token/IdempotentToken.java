package cn.slainc.idempotent.token;

public record IdempotentToken(String token, String headerName, long ttlSeconds, long expireAt) {
}
