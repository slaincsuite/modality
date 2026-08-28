package cn.slainc.idempotent.support;

import cn.slainc.core.constant.CacheKeys;
import cn.slainc.utils.digest.DigestUtils;

public final class IdempotentKeys {

    public static final String FAILURE_SUFFIX = ":fail";

    private IdempotentKeys() {
    }

    public static String tokenKey(String token) {
        return CacheKeys.IDEMPOTENT + "token:" + DigestUtils.md5(token == null ? "" : token);
    }

    public static String failureKey(String key) {
        return key + FAILURE_SUFFIX;
    }

    public static String stateKey(String prefix, String fingerprint) {
        String actualPrefix = prefix == null || prefix.isBlank() ? CacheKeys.IDEMPOTENT : prefix;
        String actualFingerprint = fingerprint == null || fingerprint.isBlank()
                ? DigestUtils.md5("empty") : fingerprint;
        return actualPrefix + actualFingerprint;
    }
}
