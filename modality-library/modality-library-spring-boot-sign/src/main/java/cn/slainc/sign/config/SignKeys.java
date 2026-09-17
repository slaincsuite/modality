package cn.slainc.sign.config;

import cn.slainc.core.constant.CacheKeys;

public final class SignKeys {

    public static final String PREFIX = CacheKeys.SIGN;

    public static final String KEYS_CONFIG_KEY = "modality.sign.keys";

    public static final String ALLOWED_ALGORITHMS_CONFIG_KEY = "modality.sign.allowed-algorithms";

    public static String secretConfigKey(String appId) {
        return KEYS_CONFIG_KEY + "." + appId + ".secret";
    }

    public static String nonceKey(String appId, String nonce) {
        return PREFIX + "nonce:" + appId + ":" + nonce;
    }

    public static String failKey(String appId) {
        return PREFIX + "fail:" + appId;
    }

    private SignKeys() {
    }
}
