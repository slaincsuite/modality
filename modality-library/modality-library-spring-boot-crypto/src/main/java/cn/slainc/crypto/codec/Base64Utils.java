package cn.slainc.crypto.codec;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class Base64Utils {

    private static final Base64.Encoder STANDARD_ENCODER = Base64.getEncoder();

    private static final Base64.Decoder STANDARD_DECODER = Base64.getDecoder();

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder();

    private static final Base64.Encoder URL_ENCODER_NO_PADDING = Base64.getUrlEncoder().withoutPadding();

    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    private Base64Utils() {
    }

    public static String encode(byte[] bytes) {
        return bytes == null ? null : STANDARD_ENCODER.encodeToString(bytes);
    }

    public static String encode(String text) {
        return text == null ? null : encode(text.getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] decode(String text) {
        return text == null ? null : STANDARD_DECODER.decode(text.trim());
    }

    public static String decodeToString(String text) {
        byte[] bytes = decode(text);
        return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
    }

    public static String encodeUrl(byte[] bytes) {
        return bytes == null ? null : URL_ENCODER.encodeToString(bytes);
    }

    public static String encodeUrlNoPadding(byte[] bytes) {
        return bytes == null ? null : URL_ENCODER_NO_PADDING.encodeToString(bytes);
    }

    public static byte[] decodeUrl(String text) {
        if (text == null) {
            return null;
        }
        String value = text.trim();
        return URL_DECODER.decode(pad(value));
    }

    public static String pad(String text) {
        int remainder = text.length() & 3;
        if (remainder == 0) {
            return text;
        }
        if (remainder == 1) {
            throw new IllegalArgumentException("非法的 base64url 长度：" + text.length());
        }
        return text + "=".repeat(4 - remainder);
    }
}
