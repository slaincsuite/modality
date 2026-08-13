package cn.slainc.utils.net;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetUtilsTest {

    @Test
    void privateAndPublicRanges() {
        assertTrue(IpUtils.isPrivateIp("192.168.1.1"));
        assertTrue(IpUtils.isPrivateIp("10.0.0.5"));
        assertTrue(IpUtils.isPrivateIp("172.16.3.4"));
        assertTrue(IpUtils.isPrivateIp("127.0.0.1"));
        assertFalse(IpUtils.isPrivateIp("8.8.8.8"));
        assertTrue(IpUtils.isPublicIp("8.8.8.8"));
        assertFalse(IpUtils.isPublicIp("192.168.1.1"));
        assertFalse(IpUtils.isPrivateIp("not-an-ip"));
        assertTrue(IpUtils.isLocalhost("127.0.0.1"));
        assertTrue(IpUtils.isIpv6("::1"));
    }

    @Test
    void ipConversion() {
        assertEquals(1L, IpUtils.ipToLong("0.0.0.1"));
        assertEquals("0.0.0.1", IpUtils.longToIp(1L));
        assertEquals("192.168.1.1", IpUtils.longToIp(IpUtils.ipToLong("192.168.1.1")));
        assertTrue(IpUtils.isInRange("192.168.1.5", "192.168.1.0/24"));
        assertFalse(IpUtils.isInRange("192.168.2.5", "192.168.1.0/24"));
        assertFalse(IpUtils.isInRange("bad", "192.168.1.0/24"));
        assertTrue(IpUtils.isInRange("10.1.2.3", "10.0.0.0/8"));
    }

    @Test
    void clientIpResolution() {
        assertEquals("1.1.1.1", IpUtils.resolveClientIp("1.1.1.1, 2.2.2.2", null, null, "127.0.0.1"));
        assertEquals("3.3.3.3", IpUtils.resolveClientIp("1.1.1.1", "3.3.3.3", null, "127.0.0.1"));
        assertEquals("4.4.4.4", IpUtils.resolveClientIp(null, null, "4.4.4.4", "127.0.0.1"));
        assertEquals("127.0.0.1", IpUtils.resolveClientIp("unknown", null, null, "127.0.0.1"));
        assertEquals(2, IpUtils.splitProxyChain("1.1.1.1, unknown, 2.2.2.2").size());
    }

    @Test
    void urlHelpers() {
        String url = "https://example.com:8443/api/v1/users?page=1&size=10";
        assertEquals("example.com", UrlUtils.getHost(url));
        assertEquals(8443, UrlUtils.getPort(url));
        assertEquals("/api/v1/users", UrlUtils.getPath(url));
        Map<String, String> params = UrlUtils.getQueryParams(url);
        assertEquals("1", params.get("page"));
        assertEquals("10", params.get("size"));
        assertTrue(UrlUtils.isValid(url));
        assertFalse(UrlUtils.isValid("not a url"));
        assertTrue(UrlUtils.appendQueryParams("https://a.com/x", Map.of("k", "v")).contains("k=v"));
        assertEquals("https://a.com/x/y", UrlUtils.join("https://a.com/", "x", "/y/"));
        assertEquals(3, UrlUtils.getPathSegments(url).size());
        assertNull(UrlUtils.parse(null));
    }

    @Test
    void httpStatusHelpers() {
        assertEquals(200, HttpStatus.OK.code());
        assertEquals("资源不存在", HttpStatus.reasonOf(404));
        assertEquals("请求参数错误", HttpStatus.of(400).reason());
        assertTrue(HttpStatus.isSuccess(204));
        assertTrue(HttpStatus.isRedirect(302));
        assertTrue(HttpStatus.isClientError(404));
        assertTrue(HttpStatus.isServerError(503));
        assertFalse(HttpStatus.isSuccess(404));
        assertNull(HttpStatus.of(999));
    }
}
