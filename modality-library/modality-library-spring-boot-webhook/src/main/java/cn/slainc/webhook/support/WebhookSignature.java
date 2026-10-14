package cn.slainc.webhook.support;

import cn.slainc.utils.string.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Locale;

public final class WebhookSignature {

    public static final String TIMESTAMP_PREFIX = "t=";

    public static final String SIGNATURE_PREFIX = "v1=";

    public static final String ALGORITHM = "HmacSHA256";

    private WebhookSignature() {
    }

    public static String sign(String secret, long timestampSeconds, String body) {
        if (StringUtils.isBlank(secret)) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            String content = timestampSeconds + "." + StringUtils.nullToEmpty(body);
            return toHex(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 不可用", e);
        }
    }

    public static String header(String secret, long timestampSeconds, String body) {
        String signature = sign(secret, timestampSeconds, body);
        if (signature == null) {
            return null;
        }
        return TIMESTAMP_PREFIX + timestampSeconds + "," + SIGNATURE_PREFIX + signature;
    }

    public static boolean verify(String secret, String headerValue, String body, long toleranceSeconds) {
        if (StringUtils.isBlank(secret) || StringUtils.isBlank(headerValue)) {
            return false;
        }
        Long timestamp = null;
        String signature = null;
        for (String part : headerValue.split(",")) {
            String trimmed = part.trim();
            if (trimmed.startsWith(TIMESTAMP_PREFIX)) {
                timestamp = toLongOrNull(trimmed.substring(TIMESTAMP_PREFIX.length()).trim());
            } else if (trimmed.startsWith(SIGNATURE_PREFIX)) {
                signature = trimmed.substring(SIGNATURE_PREFIX.length()).trim();
            }
        }
        if (timestamp == null || StringUtils.isBlank(signature)) {
            return false;
        }
        if (toleranceSeconds >= 0 && Math.abs(Instant.now().getEpochSecond() - timestamp) > toleranceSeconds) {
            return false;
        }
        String expected = sign(secret, timestamp, body);
        if (expected == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                signature.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
    }

    private static Long toLongOrNull(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(Character.forDigit((value >> 4) & 0xF, 16));
            builder.append(Character.forDigit(value & 0xF, 16));
        }
        return builder.toString();
    }
}
