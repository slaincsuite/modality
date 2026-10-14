package cn.slainc.webhook.support;

import cn.slainc.redis.lock.DistributedLockTemplate;
import cn.slainc.redis.lock.LockHandle;
import cn.slainc.redis.lock.LockType;
import cn.slainc.webhook.config.WebhookProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebhookRetrySchedulerTest {

    private final WebhookDeliveryDispatcher dispatcher = mock(WebhookDeliveryDispatcher.class);

    private final WebhookProperties properties = new WebhookProperties();

    @SuppressWarnings("unchecked")
    private ObjectProvider<DistributedLockTemplate> lockProvider(DistributedLockTemplate template) {
        ObjectProvider<DistributedLockTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(template);
        return provider;
    }

    @Test
    void shouldRetryWhileHoldingTheDistributedLock() {
        DistributedLockTemplate template = mock(DistributedLockTemplate.class);
        LockHandle handle = mock(LockHandle.class);
        when(handle.isLocked()).thenReturn(true);
        when(template.acquire(anyString(), anyLong(), anyLong(), any(LockType.class))).thenReturn(handle);
        when(dispatcher.retryDue(anyInt())).thenReturn(3);

        WebhookRetryScheduler scheduler = new WebhookRetryScheduler(dispatcher, properties,
                lockProvider(template), null);

        assertThat(scheduler.retryDue()).isEqualTo(3);
        verify(dispatcher).retryDue(properties.getBatchSize());
        verify(template).acquire(WebhookRetryScheduler.LOCK_KEY, 0L, 300L, LockType.REENTRANT);
        verify(handle).close();
    }

    @Test
    void shouldSkipTheScanWhenAnotherNodeHoldsTheLock() {
        DistributedLockTemplate template = mock(DistributedLockTemplate.class);
        LockHandle handle = mock(LockHandle.class);
        when(handle.isLocked()).thenReturn(false);
        when(template.acquire(anyString(), anyLong(), anyLong(), any(LockType.class))).thenReturn(handle);

        WebhookRetryScheduler scheduler = new WebhookRetryScheduler(dispatcher, properties,
                lockProvider(template), null);

        assertThat(scheduler.retryDue()).isZero();
        verify(dispatcher, never()).retryDue(anyInt());
        verify(handle, never()).close();
    }

    @Test
    void shouldFallBackToLocalRetryWhenAcquireFails() {
        DistributedLockTemplate template = mock(DistributedLockTemplate.class);
        when(template.acquire(anyString(), anyLong(), anyLong(), any(LockType.class)))
                .thenThrow(new RuntimeException("redis down"));
        when(dispatcher.retryDue(anyInt())).thenReturn(2);

        WebhookRetryScheduler scheduler = new WebhookRetryScheduler(dispatcher, properties,
                lockProvider(template), null);

        assertThat(scheduler.retryDue()).isEqualTo(2);
    }

    @Test
    void shouldRetryWithoutALockWhenRedisIsAbsent() {
        when(dispatcher.retryDue(anyInt())).thenReturn(2);

        WebhookRetryScheduler scheduler = new WebhookRetryScheduler(dispatcher, properties,
                lockProvider(null), null);

        assertThat(scheduler.retryDue()).isEqualTo(2);
    }

    @Test
    void shouldSwallowScanFailures() {
        when(dispatcher.retryDue(anyInt())).thenThrow(new IllegalStateException("db down"));

        WebhookRetryScheduler scheduler = new WebhookRetryScheduler(dispatcher, properties,
                lockProvider(null), null);

        assertThat(scheduler.retryDue()).isZero();
    }

    @Test
    void shouldNotScheduleWhenTheIntervalIsDisabled() {
        properties.setRetryInterval(Duration.ZERO);

        WebhookRetryScheduler scheduler = new WebhookRetryScheduler(dispatcher, properties,
                lockProvider(null), null);

        scheduler.start();

        verify(dispatcher, never()).retryDue(anyInt());
    }
}
