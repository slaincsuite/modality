package cn.slainc.session.query;

public final class SessionTokens {

    private SessionTokens() {
    }

    public static String strip(String key, String prefix) {
        if (key == null) {
            return null;
        }
        return prefix != null && !prefix.isEmpty() && key.startsWith(prefix) ? key.substring(prefix.length()) : key;
    }

    public static String mask(String tokenValue) {
        if (tokenValue == null || tokenValue.isBlank()) {
            return tokenValue;
        }
        int length = tokenValue.length();
        if (length <= 8) {
            return tokenValue.substring(0, Math.min(2, length)) + "****";
        }
        return tokenValue.substring(0, 6) + "******" + tokenValue.substring(length - 4);
    }
}
