package cn.slainc.web.header;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SecurityHeadersFilterTest {

    private MockHttpServletResponse run(SecurityHeadersProperties properties, MockHttpServletRequest request)
            throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        new SecurityHeadersFilter(properties, -8000).doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void appliesDefaultHeaders() throws Exception {
        MockHttpServletResponse response = run(new SecurityHeadersProperties(),
                new MockHttpServletRequest("GET", "/api/users"));
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        assertEquals("SAMEORIGIN", response.getHeader("X-Frame-Options"));
        assertEquals("strict-origin-when-cross-origin", response.getHeader("Referrer-Policy"));
        assertNull(response.getHeader("Strict-Transport-Security"));
        assertNull(response.getHeader("Content-Security-Policy"));
    }

    @Test
    void hstsOnlyForSecureRequests() throws Exception {
        SecurityHeadersProperties properties = new SecurityHeadersProperties();
        properties.setHstsEnabled(true);

        MockHttpServletRequest insecure = new MockHttpServletRequest("GET", "/api/users");
        assertNull(run(properties, insecure).getHeader("Strict-Transport-Security"));

        MockHttpServletRequest secure = new MockHttpServletRequest("GET", "/api/users");
        secure.setSecure(true);
        assertEquals("max-age=31536000; includeSubDomains",
                run(properties, secure).getHeader("Strict-Transport-Security"));

        MockHttpServletRequest forwarded = new MockHttpServletRequest("GET", "/api/users");
        forwarded.addHeader("X-Forwarded-Proto", "https");
        assertEquals("max-age=31536000; includeSubDomains",
                run(properties, forwarded).getHeader("Strict-Transport-Security"));
    }

    @Test
    void appliesContentSecurityPolicyWhenConfigured() throws Exception {
        SecurityHeadersProperties properties = new SecurityHeadersProperties();
        properties.setContentSecurityPolicy("default-src 'self'");
        assertEquals("default-src 'self'",
                run(properties, new MockHttpServletRequest("GET", "/x")).getHeader("Content-Security-Policy"));
    }
}
