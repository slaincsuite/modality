package cn.slainc.web.limit;

import cn.slainc.core.exception.SystemException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcurrencyLimitInterceptorTest {

    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @Test
    void disabledByDefault() {
        ConcurrencyLimitInterceptor interceptor = new ConcurrencyLimitInterceptor(new RequestLimitProperties());
        for (int i = 0; i < 100; i++) {
            assertTrue(interceptor.preHandle(request, response, new Object()));
        }
        assertEquals(0, interceptor.currentGlobal());
    }

    @Test
    void rejectsWhenGlobalLimitExceeded() {
        RequestLimitProperties properties = new RequestLimitProperties();
        properties.setEnabled(true);
        properties.setMaxConcurrentGlobal(2);
        ConcurrencyLimitInterceptor interceptor = new ConcurrencyLimitInterceptor(properties);

        assertTrue(interceptor.preHandle(request, response, new Object()));
        assertTrue(interceptor.preHandle(request, response, new Object()));
        assertEquals(2, interceptor.currentGlobal());

        SystemException exception = assertThrows(SystemException.class,
                () -> interceptor.preHandle(request, response, new Object()));
        assertEquals(properties.getMessage(), exception.getMessage());
        assertEquals(2, interceptor.currentGlobal(), "被拒绝的请求不应占用计数");

        interceptor.afterCompletion(request, response, new Object(), null);
        assertEquals(1, interceptor.currentGlobal());
    }

    @Test
    void rejectsWhenPerIpLimitExceeded() {
        RequestLimitProperties properties = new RequestLimitProperties();
        properties.setEnabled(true);
        properties.setMaxConcurrentPerIp(1);
        properties.setMaxConcurrentGlobal(100);
        ConcurrencyLimitInterceptor interceptor = new ConcurrencyLimitInterceptor(properties);

        assertTrue(interceptor.preHandle(request, response, new Object()));
        assertThrows(SystemException.class, () -> interceptor.preHandle(request, response, new Object()));

        interceptor.afterCompletion(request, response, new Object(), null);
        assertEquals(0, interceptor.currentGlobal());
    }

    @Test
    void excludesConfiguredPaths() {
        RequestLimitProperties properties = new RequestLimitProperties();
        properties.setEnabled(true);
        properties.setMaxConcurrentGlobal(1);
        ConcurrencyLimitInterceptor interceptor = new ConcurrencyLimitInterceptor(properties);
        MockHttpServletRequest actuator = new MockHttpServletRequest("GET", "/actuator/health");

        assertTrue(interceptor.preHandle(actuator, response, new Object()));
        assertTrue(interceptor.preHandle(actuator, response, new Object()));
        assertEquals(0, interceptor.currentGlobal());
    }
}
