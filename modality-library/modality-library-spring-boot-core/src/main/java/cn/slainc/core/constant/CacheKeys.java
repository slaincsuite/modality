package cn.slainc.core.constant;

public final class CacheKeys {

    public static final String PREFIX = "modality:";
    public static final String CONFIG = PREFIX + "config:";
    public static final String TOKEN = PREFIX + "token:";
    public static final String USER = PREFIX + "user:";
    public static final String TENANT = PREFIX + "tenant:";
    public static final String DICT = PREFIX + "dict:";
    public static final String LOCK = PREFIX + "lock:";
    public static final String RATE_LIMIT = PREFIX + "ratelimit:";
    public static final String IDEMPOTENT = PREFIX + "idempotent:";
    public static final String CAPTCHA = PREFIX + "captcha:";
    public static final String API_KEY = PREFIX + "apikey:";
    public static final String SEQUENCE = PREFIX + "sequence:";
    public static final String SIGN = PREFIX + "sign:";
    public static final String ID_WORKER = PREFIX + "id:worker:";

    private CacheKeys() {
    }

    public static String key(String... parts) {
        StringBuilder builder = new StringBuilder(PREFIX);
        if (parts != null) {
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) {
                    builder.append(':');
                }
                builder.append(parts[i]);
            }
        }
        return builder.toString();
    }
}
