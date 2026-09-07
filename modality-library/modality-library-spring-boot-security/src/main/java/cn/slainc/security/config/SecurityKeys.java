package cn.slainc.security.config;

public final class SecurityKeys {

    public static final String PREFIX = "modality:security:";

    public static final String DAO_PROBE_KEY = PREFIX + "dao:probe";

    private SecurityKeys() {
    }

    public static String permissionKey(String type, Object loginId) {
        return PREFIX + "perm:" + type + ":" + loginId;
    }

    public static String lockFailKey(String type, Object loginId) {
        return PREFIX + "lock:fail:" + type + ":" + loginId;
    }

    public static String lockLockedKey(String type, Object loginId) {
        return PREFIX + "lock:locked:" + type + ":" + loginId;
    }

    public static String lastIpKey(String type, Object loginId) {
        return PREFIX + "lastip:" + type + ":" + loginId;
    }

    public static String kickReasonKey(String tokenValue) {
        return PREFIX + "kick:reason:" + tokenValue;
    }

    public static String passwordHistoryKey(String type, Object userId) {
        return PREFIX + "pwd:hist:" + type + ":" + userId;
    }
}
