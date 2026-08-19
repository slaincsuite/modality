package cn.slainc.web.body;

import cn.slainc.utils.json.JsonUtils;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.unit.DataSize;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestBodyLimitFilterTest {

    private final RequestBodyProperties properties = new RequestBodyProperties();

    @Test
    void rejectsOversizedRequestWithUnifiedResult() throws Exception {
        properties.setMaxRequestSize(DataSize.ofBytes(16));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/echo");
        request.setContent(new byte[32]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        new RequestBodyLimitFilter(properties).doFilter(request, response,
                (servletRequest, servletResponse) -> invoked.set(true));

        assertFalse(invoked.get(), "超限请求不应进入后续处理链");
        assertEquals(413, response.getStatus());
        Map<String, Object> body = JsonUtils.parseMap(response.getContentAsString(StandardCharsets.UTF_8));
        assertEquals(413000, body.get("code"));
        assertEquals("请求体过大", body.get("message"));
    }

    @Test
    void passesRequestWithinLimit() throws Exception {
        properties.setMaxRequestSize(DataSize.ofKilobytes(1));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/echo");
        request.setContent(new byte[128]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        new RequestBodyLimitFilter(properties).doFilter(request, response,
                (servletRequest, servletResponse) -> invoked.set(true));

        assertTrue(invoked.get());
        assertEquals(200, response.getStatus());
    }

    @Test
    void skipsExcludedPath() throws Exception {
        properties.setMaxRequestSize(DataSize.ofBytes(16));
        properties.setExcludePaths(java.util.List.of("/inner/**"));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/inner/echo");
        request.setContent(new byte[32]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        new RequestBodyLimitFilter(properties).doFilter(request, response,
                (servletRequest, servletResponse) -> invoked.set(true));

        assertTrue(invoked.get());
    }

    @Test
    void skipsCheckWhenDisabled() throws Exception {
        properties.setEnabled(false);
        properties.setMaxRequestSize(DataSize.ofBytes(16));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/echo");
        request.setContent(new byte[32]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        new RequestBodyLimitFilter(properties).doFilter(request, response,
                (servletRequest, servletResponse) -> invoked.set(true));

        assertTrue(invoked.get());
    }
}
