package cn.slainc.mfa.totp;

import cn.slainc.utils.string.StringUtils;

import java.nio.charset.StandardCharsets;

public class OtpAuthUrlBuilder {

    public static final String ALGORITHM = "SHA1";

    public static final int DEFAULT_DIGITS = 6;

    public static final int DEFAULT_PERIOD_SECONDS = 30;

    private static final String DEFAULT_ISSUER = "modality";

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    public String build(String issuer, String account, String secret) {
        return build(issuer, account, secret, DEFAULT_DIGITS, DEFAULT_PERIOD_SECONDS);
    }

    public String build(String issuer, String account, String secret, int digits, int periodSeconds) {
        String effectiveIssuer = StringUtils.defaultIfBlank(issuer, DEFAULT_ISSUER);
        String encodedIssuer = encode(effectiveIssuer);
        return "otpauth://totp/" + encodedIssuer + ":" + encode(StringUtils.trimToEmpty(account))
                + "?secret=" + encode(StringUtils.trimToEmpty(secret))
                + "&issuer=" + encodedIssuer
                + "&algorithm=" + ALGORITHM + "&digits=" + digits + "&period=" + periodSeconds;
    }

    static String encode(String value) {
        if (StringUtils.isEmpty(value)) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (byte item : value.getBytes(StandardCharsets.UTF_8)) {
            int current = item & 0xFF;
            if (isUnreserved(current) || current == '@' || current == ':') {
                builder.append((char) current);
            } else {
                builder.append('%').append(HEX[(current >> 4) & 0x0F]).append(HEX[current & 0x0F]);
            }
        }
        return builder.toString();
    }

    private static boolean isUnreserved(int value) {
        return (value >= 'A' && value <= 'Z')
                || (value >= 'a' && value <= 'z')
                || (value >= '0' && value <= '9')
                || value == '-' || value == '.' || value == '_' || value == '~';
    }
}
