package cn.slainc.web.websocket;

import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebSocketAuthenticatorTest {

    private final WebSocketProperties properties = new WebSocketProperties();

    @Test
    void resolvesTokenFromQueryParameter() {
        WebSocketUser user = new TokenWebSocketAuthenticator(properties).authenticate(handshake("token=abc", null));

        assertEquals("abc", user.userId());
    }

    @Test
    void resolvesTokenFromAuthorizationHeader() {
        WebSocketUser user = new TokenWebSocketAuthenticator(properties).authenticate(handshake(null, "Bearer xyz"));

        assertEquals("xyz", user.userId());
    }

    @Test
    void rejectsMissingToken() {
        assertNull(new TokenWebSocketAuthenticator(properties).authenticate(handshake(null, null)));
        assertNull(new TokenWebSocketAuthenticator(properties).authenticate(handshake("token=", "Bearer   ")));
    }

    @Test
    void rejectsHandshakeWhenAuthenticatorReturnsNull() throws Exception {
        WebSocketHandshakeInterceptor interceptor = new WebSocketHandshakeInterceptor(handshake -> null);
        MockHttpServletResponse rawResponse = new MockHttpServletResponse();
        Map<String, Object> attributes = new HashMap<>();

        boolean allowed = interceptor.beforeHandshake(new ServletServerHttpRequest(new MockHttpServletRequest()),
                new ServletServerHttpResponse(rawResponse), null, attributes);

        assertFalse(allowed);
        assertEquals(401, rawResponse.getStatus());
        assertTrue(attributes.isEmpty());
    }

    @Test
    void storesUserWhenAuthenticatorSucceeds() throws Exception {
        WebSocketUser user = new WebSocketUser("u1", "张三");
        WebSocketHandshakeInterceptor interceptor = new WebSocketHandshakeInterceptor(handshake -> user);
        MockHttpServletResponse rawResponse = new MockHttpServletResponse();
        Map<String, Object> attributes = new HashMap<>();

        boolean allowed = interceptor.beforeHandshake(new ServletServerHttpRequest(new MockHttpServletRequest()),
                new ServletServerHttpResponse(rawResponse), null, attributes);

        assertTrue(allowed);
        assertSame(user, attributes.get(WebSocketHandshakeInterceptor.USER_ATTRIBUTE));
    }

    @Test
    void rejectsHandshakeWhenAuthenticatorThrows() throws Exception {
        WebSocketHandshakeInterceptor interceptor = new WebSocketHandshakeInterceptor(handshake -> {
            throw new IllegalStateException("令牌解析失败");
        });
        MockHttpServletResponse rawResponse = new MockHttpServletResponse();

        boolean allowed = interceptor.beforeHandshake(new ServletServerHttpRequest(new MockHttpServletRequest()),
                new ServletServerHttpResponse(rawResponse), null, new HashMap<>());

        assertFalse(allowed);
        assertEquals(401, rawResponse.getStatus());
    }

    private WebSocketHandshake handshake(String queryString, String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ws");
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(8080);
        if (queryString != null) {
            request.setQueryString(queryString);
        }
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        return new WebSocketHandshake(new ServletServerHttpRequest(request), new HashMap<>());
    }
}
