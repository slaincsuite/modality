package cn.slainc.idempotent.spi;

public record IdempotentKey(String stateKey, String fingerprint, String token) {

    public boolean hasToken() {
        return token != null && !token.isBlank();
    }
}
