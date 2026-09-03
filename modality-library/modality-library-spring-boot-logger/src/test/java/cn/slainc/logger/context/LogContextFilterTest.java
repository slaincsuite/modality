package cn.slainc.logger.context;

import cn.slainc.core.constant.SystemConstants;
import cn.slainc.core.context.RequestContext;
import cn.slainc.core.context.TenantContext;
import cn.slainc.core.context.UserContext;
import cn.slainc.logger.autoconfigure.LoggerProperties;
import cn.slainc.logger.constant.LogHeaders;
import cn.slainc.logger.constant.LogMdcKeys;
import cn.slainc.logger.trace.SpanFilter;
import cn.slainc.logger.trace.SpanIdGenerator;
import cn.slainc.web.filter.FilterOrder;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogContextFilterTest {

    private final LoggerProperties properties = new LoggerProperties();

    @AfterEach
    void tearDown() {
        MDC.clear();
        RequestContext.clear();
        UserContext.clear();
        TenantContext.clear();
    }

    private void prepareContext() {
        RequestContext.set(new RequestContext.RequestInfo("trace-mdc-1", "10.0.0.1",
                "Mozilla/5.0 (Macintosh)", "POST", "/api/orders"));
        UserContext.set(UserContext.UserInfo.of(1001L, "alice", null));
        TenantContext.set(9L);
    }

    @Test
    void injectsRequestContextIntoMdcAndClearsItAfterwards() throws Exception {
        prepareContext();
        LogContextFilter filter = new LogContextFilter(properties.getContext(), () -> "1001",
                FilterOrder.SA_TOKEN + 500);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.addHeader(LogHeaders.DEVICE_ID, "device-9");
        AtomicReference<Map<String, String>> inside = new AtomicReference<>();
        FilterChain chain = (req, res) -> inside.set(MDC.getCopyOfContextMap());

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        Map<String, String> captured = inside.get();
        assertNotNull(captured, "请求处理期间 MDC 必须有值");
        assertEquals("10.0.0.1", captured.get(LogMdcKeys.CLIENT_IP));
        assertEquals("POST", captured.get(LogMdcKeys.HTTP_METHOD));
        assertEquals("/api/orders", captured.get(LogMdcKeys.REQUEST_PATH));
        assertEquals("device-9", captured.get(LogMdcKeys.DEVICE_ID));
        assertEquals("1001", captured.get(LogMdcKeys.LOGIN_ID));
        assertEquals("alice", captured.get(LogMdcKeys.USERNAME));
        assertEquals("9", captured.get(SystemConstants.TENANT_ID_MDC_KEY));
        assertEquals("1001", captured.get(SystemConstants.USER_ID_MDC_KEY));
        assertTrue(captured.get(LogMdcKeys.USER_AGENT).startsWith("Mozilla/5.0"));

        assertMdcEmpty();
    }

    @Test
    void clearsMdcEvenWhenChainThrows() {
        prepareContext();
        LogContextFilter filter = new LogContextFilter(properties.getContext(), () -> "1001",
                FilterOrder.SA_TOKEN + 500);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/boom");
        FilterChain chain = (req, res) -> {
            throw new IllegalStateException("业务处理失败");
        };
        assertThrows(IllegalStateException.class,
                () -> filter.doFilter(request, new MockHttpServletResponse(), chain));
        assertMdcEmpty();
    }

    @Test
    void keepsUnrelatedMdcKeysUntouched() throws Exception {
        prepareContext();
        MDC.put("bizKey", "业务自定义值");
        LogContextFilter filter = new LogContextFilter(properties.getContext(), () -> "1001",
                FilterOrder.SA_TOKEN + 500);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
        });
        assertEquals("业务自定义值", MDC.get("bizKey"), "过滤器只能清理自己写入的键");
        assertNull(MDC.get(LogMdcKeys.CLIENT_IP));
        assertNull(MDC.get(LogMdcKeys.REQUEST_PATH));
    }

    @Test
    void spanFilterAlsoClearsSpanKeysAfterRequest() throws Exception {
        LoggerProperties.Trace trace = properties.getTrace();
        SpanFilter filter = new SpanFilter(trace, SpanIdGenerator.UUID_GENERATOR, FilterOrder.TRACE + 1);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        request.addHeader(trace.getSpanHeaderName(), "upstream-span");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Map<String, String>> inside = new AtomicReference<>();
        filter.doFilter(request, response, (req, res) -> inside.set(MDC.getCopyOfContextMap()));

        assertNotNull(inside.get().get(LogMdcKeys.SPAN_ID));
        assertEquals("upstream-span", inside.get().get(LogMdcKeys.PARENT_SPAN_ID));
        assertEquals(inside.get().get(LogMdcKeys.SPAN_ID), response.getHeader(trace.getSpanHeaderName()));
        assertNull(MDC.get(LogMdcKeys.SPAN_ID));
        assertNull(MDC.get(LogMdcKeys.PARENT_SPAN_ID));
    }

    private void assertMdcEmpty() {
        Map<String, String> remaining = MDC.getCopyOfContextMap();
        assertTrue(remaining == null || remaining.isEmpty(), "请求结束后 MDC 必须为空，否则线程复用会串数据，实际残留：" + remaining);
    }
}
