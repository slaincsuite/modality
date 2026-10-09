package cn.slainc.modules.infra.service;

import cn.slainc.core.lang.BizAssert;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

public final class PreviewGuard {

    public static final String CONTENT_SECURITY_POLICY = "default-src 'none'; sandbox";

    public static final String CONTENT_TYPE_OPTIONS = "nosniff";

    private static final Set<String> INLINE_TYPES = Set.of("image/png", "image/jpeg", "image/gif", "image/webp",
            "text/plain");

    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private static final byte[] GIF_87A = "GIF87a".getBytes(StandardCharsets.US_ASCII);

    private static final byte[] GIF_89A = "GIF89a".getBytes(StandardCharsets.US_ASCII);

    private static final byte[] RIFF_MAGIC = "RIFF".getBytes(StandardCharsets.US_ASCII);

    private static final byte[] WEBP_MAGIC = "WEBP".getBytes(StandardCharsets.US_ASCII);

    private static final int MARKUP_SCAN_BYTES = 512;

    private static final Set<String> TEXT_MARKUP_SIGNATURES = Set.of("<html", "<!doctype", "<script", "<svg",
            "<iframe", "<body", "<object", "<embed", "<?xml");

    private PreviewGuard() {
    }

    public static String normalize(String contentType) {
        if (contentType == null) {
            return "";
        }
        int index = contentType.indexOf(';');
        String base = index < 0 ? contentType : contentType.substring(0, index);
        return base.trim().toLowerCase(Locale.ROOT);
    }

    public static String requireInlineAllowed(String contentType) {
        String normalized = normalize(contentType);
        BizAssert.isTrue(INLINE_TYPES.contains(normalized), "该文件类型不允许在线预览：" + normalized);
        return normalized;
    }

    public static void assertMagicMatches(String contentType, byte[] data) {
        boolean matched = switch (normalize(contentType)) {
            case "image/png" -> startsWith(data, PNG_MAGIC);
            case "image/jpeg" -> data != null && data.length >= 3 && (data[0] & 0xFF) == 0xFF
                    && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF;
            case "image/gif" -> startsWith(data, GIF_87A) || startsWith(data, GIF_89A);
            case "image/webp" -> data != null && data.length >= 12 && matchesAt(data, 0, RIFF_MAGIC)
                    && matchesAt(data, 8, WEBP_MAGIC);
            case "text/plain" -> !containsMarkup(data);
            default -> false;
        };
        BizAssert.isTrue(matched, "文件内容与声明的类型不一致，已拒绝在线预览：" + normalize(contentType));
    }

    private static boolean startsWith(byte[] data, byte[] magic) {
        return matchesAt(data, 0, magic);
    }

    private static boolean matchesAt(byte[] data, int offset, byte[] magic) {
        if (data == null || data.length < offset + magic.length) {
            return false;
        }
        for (int index = 0; index < magic.length; index++) {
            if (data[offset + index] != magic[index]) {
                return false;
            }
        }
        return true;
    }

    // text/plain 已由 nosniff 阻断嗅探，这里再拦一层伪装成文本的 HTML/SVG 标记
    private static boolean containsMarkup(byte[] data) {
        if (data == null) {
            return false;
        }
        String head = new String(data, 0, Math.min(data.length, MARKUP_SCAN_BYTES), StandardCharsets.ISO_8859_1)
                .toLowerCase(Locale.ROOT);
        for (String signature : TEXT_MARKUP_SIGNATURES) {
            if (head.contains(signature)) {
                return true;
            }
        }
        return false;
    }
}
