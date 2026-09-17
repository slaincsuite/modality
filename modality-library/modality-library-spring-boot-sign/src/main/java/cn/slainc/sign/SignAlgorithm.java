package cn.slainc.sign;

import cn.slainc.crypto.hmac.HmacAlgorithm;

public enum SignAlgorithm {

    HMAC_SHA256,
    HMAC_SM3,
    RSA_SHA256,
    SM2;

    public boolean isHmac() {
        return this == HMAC_SHA256 || this == HMAC_SM3;
    }

    public HmacAlgorithm hmacAlgorithm() {
        return switch (this) {
            case HMAC_SHA256 -> HmacAlgorithm.HMAC_SHA256;
            case HMAC_SM3 -> HmacAlgorithm.HMAC_SM3;
            default -> throw new IllegalStateException("算法 " + name() + " 不是 HMAC，无法取 HmacAlgorithm");
        };
    }

    public static SignAlgorithm fromName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String normalized = name.trim().replace('-', '_').toUpperCase();
        for (SignAlgorithm algorithm : values()) {
            if (algorithm.name().equals(normalized)) {
                return algorithm;
            }
        }
        return null;
    }
}
