package cn.slainc.web.compression;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.unit.DataSize;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BrotliCompressionFilterTest {

    private static final String COMPRESSED = "brotli-bytes";

    private final CompressionProperties properties = new CompressionProperties();

    private final BrotliCodec codec = mock(BrotliCodec.class);

    @BeforeEach
    void setUp() {
        properties.setMinResponseSize(DataSize.ofBytes(1));
        when(codec.available()).thenReturn(true);
        when(codec.compress(any())).thenReturn(COMPRESSED.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void compressesWhenBrotliAccepted() throws Exception {
        MockHttpServletResponse response = run(request("gzip, br"), "hello", "application/json", null);

        assertEquals("br", response.getHeader("Content-Encoding"));
        assertEquals("Accept-Encoding", response.getHeader("Vary"));
        assertEquals(COMPRESSED, response.getContentAsString());
    }

    @Test
    void keepsPlainBodyWhenBrotliNotAccepted() throws Exception {
        MockHttpServletResponse response = run(request("gzip"), "hello", "application/json", null);

        assertNull(response.getHeader("Content-Encoding"));
        assertEquals("hello", response.getContentAsString());
    }

    @Test
    void keepsPlainBodyWhenCodecUnavailable() throws Exception {
        when(codec.available()).thenReturn(false);

        MockHttpServletResponse response = run(request("br"), "hello", "application/json", null);

        assertNull(response.getHeader("Content-Encoding"));
        assertEquals("hello", response.getContentAsString());
    }

    @Test
    void keepsPlainBodyWhenAlreadyEncoded() throws Exception {
        MockHttpServletResponse response = run(request("br"), "hello", "application/json", "gzip");

        assertEquals("gzip", response.getHeader("Content-Encoding"));
        assertEquals("hello", response.getContentAsString());
    }

    @Test
    void keepsPlainBodyWhenContentTypeNotCompressible() throws Exception {
        MockHttpServletResponse response = run(request("br"), "hello", "image/png", null);

        assertNull(response.getHeader("Content-Encoding"));
    }

    @Test
    void keepsPlainBodyWhenSmallerThanThreshold() throws Exception {
        properties.setMinResponseSize(DataSize.ofKilobytes(1));

        MockHttpServletResponse response = run(request("br"), "hello", "application/json", null);

        assertNull(response.getHeader("Content-Encoding"));
    }

    @Test
    void keepsPlainBodyForWebSocketUpgrade() throws Exception {
        MockHttpServletRequest request = request("br");
        request.addHeader("Upgrade", "websocket");

        MockHttpServletResponse response = run(request, "hello", "application/json", null);

        assertNull(response.getHeader("Content-Encoding"));
    }

    @Test
    void keepsPlainBodyForExcludedUserAgent() throws Exception {
        properties.setExcludedUserAgents(List.of("curl"));
        MockHttpServletRequest request = request("br");
        request.addHeader("User-Agent", "curl/8.4.0");

        MockHttpServletResponse response = run(request, "hello", "application/json", null);

        assertNull(response.getHeader("Content-Encoding"));
    }

    @Test
    void keepsPlainBodyWhenCompressionDisabled() throws Exception {
        properties.setEnabled(false);

        MockHttpServletResponse response = run(request("br"), "hello", "application/json", null);

        assertNull(response.getHeader("Content-Encoding"));
    }

    @Test
    void parsesAcceptEncodingHeader() {
        assertTrue(BrotliCompressionFilter.acceptsBrotli("br"));
        assertTrue(BrotliCompressionFilter.acceptsBrotli("gzip, br;q=0.8"));
        assertTrue(BrotliCompressionFilter.acceptsBrotli("*"));
        assertFalse(BrotliCompressionFilter.acceptsBrotli("gzip, deflate"));
        assertFalse(BrotliCompressionFilter.acceptsBrotli("br;q=0"));
        assertFalse(BrotliCompressionFilter.acceptsBrotli(null));
        assertFalse(BrotliCompressionFilter.acceptsBrotli("   "));
    }

    private MockHttpServletRequest request(String acceptEncoding) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/payload");
        if (acceptEncoding != null) {
            request.addHeader("Accept-Encoding", acceptEncoding);
        }
        return request;
    }

    private MockHttpServletResponse run(MockHttpServletRequest request, String body, String contentType,
                                        String contentEncoding) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        new BrotliCompressionFilter(properties, codec).doFilter(request, response, (servletRequest, servletResponse) -> {
            HttpServletResponse raw = (HttpServletResponse) servletResponse;
            raw.setContentType(contentType);
            if (contentEncoding != null) {
                raw.setHeader("Content-Encoding", contentEncoding);
            }
            raw.getWriter().write(body);
        });
        return response;
    }
}
