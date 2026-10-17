package cn.slainc.services.livemonitor.support;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;

// 直播源地址探测守卫：写库与探测前都拒绝内网、回环、链路本地及常见地址混淆写法
public class StreamUrlGuard {

    private static final List<String> BLOCKED_SUFFIXES = List.of(".localhost", ".local", ".internal", ".lan",
            ".home.arpa", ".in-addr.arpa", ".ip6.arpa");

    private final HostResolver resolver;

    public StreamUrlGuard() {
        this(InetAddress::getAllByName);
    }

    StreamUrlGuard(HostResolver resolver) {
        this.resolver = resolver;
    }

    public URI validate(String url) {
        URI uri = parse(url);
        String scheme = uri.getScheme();
        if (scheme == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw invalid("直播源地址仅支持 http/https");
        }
        if (uri.getUserInfo() != null) {
            throw invalid("直播源地址不能包含用户信息");
        }
        if (uri.getPort() == 0) {
            throw invalid("直播源地址端口不合法");
        }
        String host = normalizedHost(uri.getHost());
        if (host.isEmpty()) {
            InetAddress authorityLiteral = parseLiteral(authorityHost(uri.getRawAuthority()));
            if (authorityLiteral != null) {
                rejectIfInternal(authorityLiteral);
            }
            throw invalid("直播源地址缺少主机名");
        }
        if (isBlockedName(host)) {
            throw forbidden();
        }
        InetAddress literal = parseLiteral(host);
        if (literal != null) {
            rejectIfInternal(literal);
            return uri;
        }
        if (host.indexOf(':') >= 0) {
            throw invalid("直播源地址的 IPv6 字面量不合法");
        }
        InetAddress[] addresses;
        try {
            addresses = resolver.resolve(host);
        } catch (UnknownHostException exception) {
            throw invalid("直播源地址无法解析，请检查主机名");
        }
        if (addresses == null || addresses.length == 0) {
            throw invalid("直播源地址无法解析，请检查主机名");
        }
        for (InetAddress address : addresses) {
            rejectIfInternal(address);
        }
        return uri;
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
        if (bytes.length != 16) {
            return true;
        }
        int first = bytes[0] & 0xff;
        int second = bytes[1] & 0xff;
        int third = bytes[2] & 0xff;
        int fourth = bytes[3] & 0xff;
        if ((first & 0xfe) == 0xfc) {
            return true;
        }
        if (first == 0x20 && second == 0x01 && third == 0x0d && fourth == 0xb8) {
            return true;
        }
        if (first == 0x20 && second == 0x02) {
            return true;
        }
        if (first == 0x20 && second == 0x01 && third == 0x00 && fourth == 0x00) {
            return true;
        }
        if (first == 0x00 && second == 0x64 && third == 0xff && fourth == 0x9b) {
            return true;
        }
        if (isIpv4Mapped(bytes) || isIpv4Compatible(bytes)) {
            return isInternalIpv4(bytes, 12);
        }
        return false;
    }

    private static URI parse(String url) {
        if (url == null || url.isBlank()) {
            throw invalid("直播源地址不能为空");
        }
        try {
            return new URI(url.trim());
        } catch (URISyntaxException exception) {
            throw invalid("直播源地址不是合法的 URL");
        }
    }

    private static String normalizedHost(String host) {
        if (host == null) {
            return "";
        }
        String value = host.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("[") && value.endsWith("]")) {
            value = value.substring(1, value.length() - 1);
        }
        return value.endsWith(".") ? value.substring(0, value.length() - 1) : value;
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

    private static String authorityHost(String authority) {
        if (authority == null || authority.isBlank() || authority.indexOf('@') >= 0) {
            return "";
        }
        if (authority.startsWith("[")) {
            int end = authority.indexOf(']');
            return end > 0 ? authority.substring(1, end) : authority;
        }
        int colon = authority.lastIndexOf(':');
        return colon > 0 ? authority.substring(0, colon) : authority;
    }

    private static void rejectIfInternal(InetAddress address) {
        if (isInternalAddress(address)) {
            throw forbidden();
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
        if (first == 100 && second >= 64 && second <= 127) {
            return true;
        }
        if (first == 169 && second == 254) {
            return true;
        }
        if (first == 172 && second >= 16 && second <= 31) {
            return true;
        }
        if (first == 192 && second == 168) {
            return true;
        }
        if (first == 192 && second == 0 && (third == 0 || third == 2)) {
            return true;
        }
        if (first == 192 && second == 88 && third == 99) {
            return true;
        }
        if (first == 198 && (second == 18 || second == 19)) {
            return true;
        }
        if (first == 198 && second == 51 && third == 100) {
            return true;
        }
        if (first == 203 && second == 0 && third == 113) {
            return true;
        }
        return first >= 224;
    }

    private static InetAddress parseLiteral(String host) {
        if (host.indexOf(':') >= 0) {
            if (!host.matches("[0-9a-fA-F:.]+")) {
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
        long limit = parts.length == 1 ? 0xFFFFFFFFL
                : (parts.length == 2 ? 0xFFFFFFL : (parts.length == 3 ? 0xFFFFL : 0xFFL));
        if (address < 0L || address > 0xFFFFFFFFL || values[values.length - 1] > limit) {
            return null;
        }
        return new byte[]{
                (byte) (address >>> 24),
                (byte) (address >>> 16),
                (byte) (address >>> 8),
                (byte) address
        };
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

    private static BizException invalid(String message) {
        return new BizException(ResultCode.BAD_REQUEST, message);
    }

    private static BizException forbidden() {
        return invalid("直播源地址不允许指向内网、回环或链路本地地址");
    }

    @FunctionalInterface
    interface HostResolver {

        InetAddress[] resolve(String host) throws UnknownHostException;
    }
}
