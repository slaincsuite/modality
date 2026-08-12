package cn.slainc.utils.string;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public final class EncodeUtils {

    private static final Charset GBK = Charset.forName("GBK");

    private EncodeUtils() {
    }

    public static String toUnicode(String text) {
        if (text == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder(text.length() * 6);
        for (int i = 0; i < text.length(); i++) {
            builder.append(String.format("\\u%04x", (int) text.charAt(i)));
        }
        return builder.toString();
    }

    public static String fromUnicode(String unicode) {
        if (unicode == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder(unicode.length() / 6 + 1);
        int i = 0;
        while (i < unicode.length()) {
            boolean escaped = unicode.charAt(i) == '\\'
                    && i + 5 < unicode.length()
                    && (unicode.charAt(i + 1) == 'u' || unicode.charAt(i + 1) == 'U');
            if (escaped) {
                builder.append((char) Integer.parseInt(unicode.substring(i + 2, i + 6), 16));
                i += 6;
            } else {
                builder.append(unicode.charAt(i));
                i++;
            }
        }
        return builder.toString();
    }

    public static String toGbk(String text) {
        return convert(text, StandardCharsets.UTF_8, GBK);
    }

    public static String fromGbk(String text) {
        return convert(text, GBK, StandardCharsets.UTF_8);
    }

    public static String convert(String text, Charset from, Charset to) {
        if (text == null || from == null || to == null || from.equals(to)) {
            return text;
        }
        return new String(text.getBytes(from), to);
    }

    public static String isoToUtf8(String text) {
        return convert(text, StandardCharsets.ISO_8859_1, StandardCharsets.UTF_8);
    }

    public static String utf8ToIso(String text) {
        return convert(text, StandardCharsets.UTF_8, StandardCharsets.ISO_8859_1);
    }

    public static byte[] toUtf8Bytes(String text) {
        return text == null ? null : text.getBytes(StandardCharsets.UTF_8);
    }

    public static String fromUtf8Bytes(byte[] bytes) {
        return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
    }
}
