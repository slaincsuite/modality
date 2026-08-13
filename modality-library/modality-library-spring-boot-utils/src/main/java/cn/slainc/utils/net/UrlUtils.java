package cn.slainc.utils.net;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class UrlUtils {

    private UrlUtils() {
    }

    public static URL parse(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            return URI.create(url.trim()).toURL();
        } catch (MalformedURLException | IllegalArgumentException ex) {
            throw new IllegalArgumentException("非法的 URL: " + url, ex);
        }
    }

    public static boolean isValid(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            URI uri = new URI(url.trim());
            return uri.getScheme() != null && uri.getHost() != null;
        } catch (URISyntaxException ex) {
            return false;
        }
    }

    public static String getHost(String url) {
        URL parsed = parse(url);
        return parsed == null ? null : parsed.getHost();
    }

    public static int getPort(String url) {
        URL parsed = parse(url);
        if (parsed == null) {
            return -1;
        }
        return parsed.getPort() < 0 ? parsed.getDefaultPort() : parsed.getPort();
    }

    public static String getPath(String url) {
        URL parsed = parse(url);
        return parsed == null ? null : parsed.getPath();
    }

    public static Map<String, String> getQueryParams(String url) {
        Map<String, String> result = new LinkedHashMap<>();
        URL parsed = parse(url);
        if (parsed == null || parsed.getQuery() == null) {
            return result;
        }
        for (String pair : parsed.getQuery().split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int equals = pair.indexOf('=');
            String key = equals < 0 ? pair : pair.substring(0, equals);
            String value = equals < 0 ? "" : pair.substring(equals + 1);
            result.put(decode(key), decode(value));
        }
        return result;
    }

    public static String appendQueryParams(String url, Map<String, ?> params) {
        if (url == null || params == null || params.isEmpty()) {
            return url;
        }
        StringBuilder builder = new StringBuilder(url);
        builder.append(url.contains("?") ? '&' : '?');
        boolean first = true;
        for (Map.Entry<String, ?> entry : params.entrySet()) {
            if (!first) {
                builder.append('&');
            }
            builder.append(encode(entry.getKey())).append('=').append(encode(String.valueOf(entry.getValue())));
            first = false;
        }
        return builder.toString();
    }

    public static String join(String base, String... segments) {
        if (base == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder(base.endsWith("/") ? base.substring(0, base.length() - 1) : base);
        if (segments != null) {
            for (String segment : segments) {
                if (segment == null || segment.isEmpty()) {
                    continue;
                }
                builder.append('/');
                String trimmed = segment.startsWith("/") ? segment.substring(1) : segment;
                builder.append(trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed);
            }
        }
        return builder.toString();
    }

    public static List<String> getPathSegments(String url) {
        String path = getPath(url);
        if (path == null || path.isEmpty() || path.equals("/")) {
            return List.of();
        }
        return java.util.Arrays.stream(path.split("/")).filter(segment -> !segment.isEmpty()).toList();
    }

    private static String encode(String text) {
        return text == null ? "" : cn.slainc.utils.codec.UrlCodecUtils.encode(text);
    }

    private static String decode(String text) {
        return text == null ? "" : cn.slainc.utils.codec.UrlCodecUtils.decode(text);
    }
}
