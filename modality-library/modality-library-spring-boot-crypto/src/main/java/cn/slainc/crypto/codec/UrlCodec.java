package cn.slainc.crypto.codec;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public final class UrlCodec {

    private UrlCodec() {
    }

    public static String encode(String text) {
        return encode(text, StandardCharsets.UTF_8);
    }

    public static String encode(String text, Charset charset) {
        return text == null ? null : URLEncoder.encode(text, charset);
    }

    public static String decode(String text) {
        return decode(text, StandardCharsets.UTF_8);
    }

    public static String decode(String text, Charset charset) {
        return text == null ? null : URLDecoder.decode(text, charset);
    }

    public static String encodePathSegment(String text) {
        return text == null ? null : encode(text, StandardCharsets.UTF_8).replace("+", "%20");
    }

    public static String decodePathSegment(String text) {
        return text == null ? null : decode(text.replace("+", "%2B"), StandardCharsets.UTF_8);
    }
}
