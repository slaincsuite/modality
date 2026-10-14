package cn.slainc.sse.handshake;

import cn.slainc.sse.autoconfigure.SseProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class SseAuthenticatorTest {

    private final SseProperties properties = new SseProperties();

    @Test
    void resolvesUserIdFromQueryParameter() {
        SseUser user = new TokenSseAuthenticator(properties).authenticate(request("token=123", null, null));

        assertThat(user.userId()).isEqualTo(123L);
        assertThat(user.name()).isEqualTo("123");
    }

    @Test
    void resolvesUserIdFromAuthorizationHeader() {
        SseUser user = new TokenSseAuthenticator(properties)
                .authenticate(request(null, "Authorization", "Bearer 456"));

        assertThat(user.userId()).isEqualTo(456L);
    }

    @Test
    void rejectsMissingToken() {
        TokenSseAuthenticator authenticator = new TokenSseAuthenticator(properties);

        assertThat(authenticator.authenticate(request(null, null, null))).isNull();
        assertThat(authenticator.authenticate(request("token=", "Authorization", "Bearer   "))).isNull();
    }

    @Test
    void rejectsNonNumericToken() {
        TokenSseAuthenticator authenticator = new TokenSseAuthenticator(properties);

        assertThat(authenticator.authenticate(request("token=abc", null, null))).isNull();
    }

    @Test
    void honoursCustomAuthConfig() {
        properties.getAuth().setTokenParameter("key");
        properties.getAuth().setTokenHeader("X-Token");
        properties.getAuth().setTokenPrefix("Token ");
        TokenSseAuthenticator authenticator = new TokenSseAuthenticator(properties);

        assertThat(authenticator.authenticate(request("key=9", null, null)).userId()).isEqualTo(9L);
        assertThat(authenticator.authenticate(request(null, "X-Token", "Token 8")).userId()).isEqualTo(8L);
        assertThat(authenticator.authenticate(request(null, "X-Token", "Bearer 8"))).isNull();
    }

    private SseHandshake request(String query, String headerName, String headerValue) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/sse");
        if (query != null) {
            String[] pair = query.split("=", 2);
            request.setParameter(pair[0], pair[1]);
        }
        if (headerName != null && headerValue != null) {
            request.addHeader(headerName, headerValue);
        }
        return new SseHandshake(request);
    }
}
