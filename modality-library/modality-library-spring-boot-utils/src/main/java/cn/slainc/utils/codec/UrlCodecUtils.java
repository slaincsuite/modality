package cn.slainc.utils.codec;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class UrlCodecUtils {

    private UrlCodecUtils() {
    }

    public static String encode(String text) {
        return text == null ? null : URLEncoder.encode(text, StandardCharsets.UTF_8);
    }

    public static String decode(String text) {
        return text == null ? null : URLDecoder.decode(text, StandardCharsets.UTF_8);
    }

    public static String encodePathSegment(String text) {
        return text == null ? null : encode(text).replace("+", "%20");
    }

    public static String decodePathSegment(String text) {
        return text == null ? null : decode(text.replace("%20", "+"));
    }

    public static String encodeForm(String text) {
        return encode(text);
    }

    public static String decodeForm(String text) {
        return decode(text);
    }
}
