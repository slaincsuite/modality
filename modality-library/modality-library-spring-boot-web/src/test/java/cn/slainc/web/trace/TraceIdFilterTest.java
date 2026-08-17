package cn.slainc.web.trace;

import cn.slainc.core.context.RequestContext;
import cn.slainc.web.config.TraceProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TraceIdFilterTest {

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    private MockHttpServletResponse run(TraceProperties properties, MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        new TraceIdFilter(properties, TraceIdGenerator.UUID_GENERATOR, -10000)
                .doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void generatesTraceIdAndClearsContext() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        MockHttpServletResponse response = run(new TraceProperties(), request);
        String traceId = response.getHeader("X-Trace-Id");
        assertNotNull(traceId);
        assertEquals(32, traceId.length());
        assertNull(RequestContext.traceId());
    }

    @Test
    void acceptsUpstreamTraceId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        request.addHeader("X-Trace-Id", "upstream-abc");
        assertEquals("upstream-abc", run(new TraceProperties(), request).getHeader("X-Trace-Id"));
    }

    @Test
    void canRejectUpstreamTraceId() throws Exception {
        TraceProperties properties = new TraceProperties();
        properties.setAcceptUpstream(false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        request.addHeader("X-Trace-Id", "upstream-abc");
        assertNotEquals("upstream-abc", run(properties, request).getHeader("X-Trace-Id"));
    }

    @Test
    void honoursCustomHeaderName() throws Exception {
        TraceProperties properties = new TraceProperties();
        properties.setHeaderName("X-Request-Trace");
        MockHttpServletResponse response = run(properties, new MockHttpServletRequest("GET", "/x"));
        assertNotNull(response.getHeader("X-Request-Trace"));
        assertNull(response.getHeader("X-Trace-Id"));
    }

    @Test
    void snowflakeGeneratorProducesNumericId() {
        String id = TraceIdGenerator.SNOWFLAKE_GENERATOR.generate();
        assertNotNull(id);
        assertEquals(id, Long.toString(Long.parseLong(id)));
    }

    @Test
    void recordsRequestContextDuringChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        request.addHeader("User-Agent", "curl/8.4.0");
        MockHttpServletResponse response = new MockHttpServletResponse();
        StringBuilder captured = new StringBuilder();
        new TraceIdFilter(new TraceProperties(), TraceIdGenerator.UUID_GENERATOR, -10000)
                .doFilter(request, response, (req, res) -> captured.append(RequestContext.traceId()));
        assertEquals(response.getHeader("X-Trace-Id"), captured.toString());
    }
}
