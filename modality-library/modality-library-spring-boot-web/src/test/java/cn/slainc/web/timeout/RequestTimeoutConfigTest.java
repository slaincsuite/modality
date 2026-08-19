package cn.slainc.web.timeout;

import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class RequestTimeoutConfigTest {

    @Test
    void appliesConfiguredAsyncTimeout() {
        RequestTimeoutProperties properties = new RequestTimeoutProperties();
        properties.setAsyncTimeout(Duration.ofMillis(1500));
        AsyncSupportConfigurer configurer = mock(AsyncSupportConfigurer.class);

        new RequestTimeoutConfig(properties).configureAsyncSupport(configurer);

        verify(configurer).setDefaultTimeout(1500L);
    }

    @Test
    void keepsExistingAsyncTimeoutWhenNotConfigured() {
        AsyncSupportConfigurer configurer = mock(AsyncSupportConfigurer.class);

        new RequestTimeoutConfig(new RequestTimeoutProperties()).configureAsyncSupport(configurer);

        verify(configurer, never()).setDefaultTimeout(anyLong());
    }

    @Test
    void runsAfterOtherMvcConfigurers() {
        assertEquals(Ordered.LOWEST_PRECEDENCE, new RequestTimeoutConfig(new RequestTimeoutProperties()).getOrder());
    }
}
