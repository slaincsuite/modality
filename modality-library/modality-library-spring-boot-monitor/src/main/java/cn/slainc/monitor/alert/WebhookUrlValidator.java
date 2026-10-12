package cn.slainc.monitor.alert;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;

public final class WebhookUrlValidator {

    private static final List<String> BLOCKED_SUFFIXES = List.of(".localhost", ".local", ".internal", ".lan",
            ".home.arpa", ".in-addr.arpa", ".ip6.arpa");

    private WebhookUrlValidator() {
    }

    public static void validate(String url) {
        validate(url, false);
    }

    public static void validate(String url, boolean allowInternalAddresses) {
        URI uri = parse(url);
        String scheme = uri.getScheme();
        if (scheme == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("Webhook 地址仅支持 http/https，当前为 "
                    + (scheme == null || scheme.isBlank() ? "空" : scheme));
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Webhook 地址缺少主机名：" + url);
        }
        if (allowInternalAddresses) {
            return;
        }
        String normalized = normalizeHost(host);
        if (isBlockedName(normalized)) {
            throw new IllegalArgumentException("Webhook 地址不允许指向内网主机：" + host);
        }
        InetAddress literal = parseLiteral(normalized);
        if (literal != null) {
            rejectIfInternal(literal, host);
            return;
        }
        if (normalized.indexOf(':') >= 0) {
            throw new IllegalArgumentException("Webhook 地址的 IPv6 字面量不合法：" + host);
        }
        for (InetAddress address : resolveQuietly(normalized)) {
            rejectIfInternal(address, host);
        }
    }

    public static boolean isInternalAddress(InetAddress address) {
        if (address == null) {
            return true;
        }
        if (address.isLoopbackAddress() || address.isAnyLocalAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            return isInternalIpv4(bytes, 0);
        }
        if (bytes.length == 16) {
            if ((bytes[0] & 0xfe) == 0xfc) {
                return true;
            }
            if (isIpv4Mapped(bytes) || isIpv4Compatible(bytes)) {
                return isInternalIpv4(bytes, 12);
            }
        }
        return false;
    }

    private static URI parse(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Webhook 地址不能为空");
        }
        try {
            return new URI(url.trim());
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Webhook 地址不是合法的 URL：" + url);
        }
    }

    private static String normalizeHost(String host) {
        String value = host.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("[") && value.endsWith("]")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static boolean isBlockedName(String host) {
        if ("localhost".equals(host) || host.isEmpty()) {
            return true;
        }
        for (String suffix : BLOCKED_SUFFIXES) {
            if (host.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    private static void rejectIfInternal(InetAddress address, String host) {
        if (isInternalAddress(address)) {
            throw new IllegalArgumentException("Webhook 地址不允许指向内网或保留地址：" + host);
        }
    }

    private static List<InetAddress> resolveQuietly(String host) {
        try {
            return List.of(InetAddress.getAllByName(host));
        } catch (UnknownHostException exception) {
            return List.of();
        }
    }

    private static boolean isIpv4Mapped(byte[] bytes) {
        for (int index = 0; index < 10; index++) {
            if (bytes[index] != 0) {
                return false;
            }
        }
        return bytes[10] == (byte) 0xff && bytes[11] == (byte) 0xff;
    }

    private static boolean isIpv4Compatible(byte[] bytes) {
        for (int index = 0; index < 12; index++) {
            if (bytes[index] != 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean isInternalIpv4(byte[] bytes, int offset) {
        int first = bytes[offset] & 0xff;
        int second = bytes[offset + 1] & 0xff;
        int third = bytes[offset + 2] & 0xff;
        if (first == 0 || first == 10 || first == 127) {
            return true;
        }
        if (first == 172 && second >= 16 && second <= 31) {
            return true;
        }
        if (first == 192 && second == 168) {
            return true;
        }
        if (first == 192 && second == 0 && third == 0) {
            return true;
        }
        if (first == 169 && second == 254) {
            return true;
        }
        if (first == 100 && second >= 64 && second <= 127) {
            return true;
        }
        if (first == 198 && (second == 18 || second == 19)) {
            return true;
        }
        return first >= 224;
    }

    private static InetAddress parseLiteral(String host) {
        if (host.indexOf(':') >= 0) {
            if (!host.matches("[0-9a-fA-F:.%]+")) {
                return null;
            }
            return literal(host);
        }
        byte[] ipv4 = parseIpv4Literal(host);
        if (ipv4 == null) {
            return null;
        }
        try {
            return InetAddress.getByAddress(ipv4);
        } catch (UnknownHostException exception) {
            return null;
        }
    }

    private static InetAddress literal(String host) {
        try {
            return InetAddress.getByName(host);
        } catch (UnknownHostException exception) {
            return null;
        }
    }

    private static byte[] parseIpv4Literal(String host) {
        String[] parts = host.split("\\.", -1);
        if (parts.length == 0 || parts.length > 4) {
            return null;
        }
        long[] values = new long[parts.length];
        for (int index = 0; index < parts.length; index++) {
            Long parsed = parseIpv4Part(parts[index]);
            if (parsed == null) {
                return null;
            }
            values[index] = parsed;
        }
        long address;
        switch (parts.length) {
            case 1 -> address = values[0];
            case 2 -> address = (values[0] << 24) | values[1];
            case 3 -> address = (values[0] << 24) | (values[1] << 16) | values[2];
            default -> address = (values[0] << 24) | (values[1] << 16) | (values[2] << 8) | values[3];
        }
        long limit = parts.length == 1 ? 0xFFFFFFFFL : (parts.length == 2 ? 0xFFFFFFL : (parts.length == 3 ? 0xFFFFL : 0xFFL));
        if (address < 0L || address > 0xFFFFFFFFL || values[values.length - 1] > limit) {
            return null;
        }
        return new byte[]{(byte) (address >>> 24), (byte) (address >>> 16), (byte) (address >>> 8), (byte) address};
    }

    private static Long parseIpv4Part(String part) {
        if (part == null || part.isEmpty()) {
            return null;
        }
        int radix = 10;
        String digits = part;
        if (part.length() > 2 && (part.startsWith("0x") || part.startsWith("0X"))) {
            radix = 16;
            digits = part.substring(2);
        } else if (part.length() > 1 && part.startsWith("0")) {
            radix = 8;
            digits = part.substring(1);
        }
        try {
            long value = Long.parseLong(digits, radix);
            return value < 0L ? null : value;
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
