package cn.slainc.web.log;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SensitiveMasker {

    public static final String MASK = "******";

    private static final List<String> SENSITIVE_KEYS = List.of(
            "password", "passwd", "pwd", "oldpassword", "newpassword", "confirmpassword",
            "token", "accesstoken", "refreshtoken", "idtoken", "secret", "appsecret", "clientsecret",
            "privatekey", "signingkey", "credential", "authorization", "cookie", "sessionid",
            "idcard", "identitycard", "bankcard", "cardno", "cvv", "mobile", "phone", "telephone", "email");

    private SensitiveMasker() {
    }

    public static boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        for (String sensitive : SENSITIVE_KEYS) {
            if (normalized.contains(sensitive)) {
                return true;
            }
        }
        return false;
    }

    public static String maskValue(String value) {
        return value == null || value.isEmpty() ? value : MASK;
    }

    public static Map<String, Object> mask(Map<String, ?> source) {
        Map<String, Object> masked = new LinkedHashMap<>();
        if (source == null) {
            return masked;
        }
        for (Map.Entry<String, ?> entry : source.entrySet()) {
            masked.put(entry.getKey(),
                    isSensitiveKey(entry.getKey()) ? MASK : entry.getValue());
        }
        return masked;
    }

    public static String maskQueryString(String queryString) {
        if (queryString == null || queryString.isBlank()) {
            return queryString;
        }
        StringBuilder builder = new StringBuilder();
        for (String pair : queryString.split("&")) {
            if (builder.length() > 0) {
                builder.append('&');
            }
            int equals = pair.indexOf('=');
            if (equals < 0) {
                builder.append(pair);
                continue;
            }
            String key = pair.substring(0, equals);
            builder.append(key).append('=').append(isSensitiveKey(key) ? MASK : pair.substring(equals + 1));
        }
        return builder.toString();
    }

    public static String truncate(String text, int maxLength) {
        if (text == null || maxLength <= 0 || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...(已截断，共 " + text.length() + " 字符)";
    }
}
