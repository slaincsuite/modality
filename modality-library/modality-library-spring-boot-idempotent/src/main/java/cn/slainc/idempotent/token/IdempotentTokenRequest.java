package cn.slainc.idempotent.token;

public record IdempotentTokenRequest(Long ttlSeconds) {
}
