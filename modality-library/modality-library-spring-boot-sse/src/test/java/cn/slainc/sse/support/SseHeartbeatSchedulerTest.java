package cn.slainc.sse.support;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SseHeartbeatSchedulerTest {

    private final ThreadPoolTaskScheduler scheduler = mock(ThreadPoolTaskScheduler.class);

    private final Runnable heartbeatTask = mock(Runnable.class);

    @Test
    void shouldScheduleAndCancelTheHeartbeat() {
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        doReturn(future).when(scheduler).scheduleAtFixedRate(any(Runnable.class), eq(Duration.ofSeconds(20)));

        SseHeartbeatScheduler heartbeat = new SseHeartbeatScheduler(scheduler, heartbeatTask, Duration.ofSeconds(20));
        assertThat(heartbeat.isRunning()).isFalse();

        heartbeat.start();

        assertThat(heartbeat.isRunning()).isTrue();
        heartbeat.start();
        verify(scheduler).scheduleAtFixedRate(any(Runnable.class), eq(Duration.ofSeconds(20)));

        heartbeat.stop();
        verify(future).cancel(false);
        assertThat(heartbeat.isRunning()).isFalse();
        assertThat(heartbeat.getPhase()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void shouldNotScheduleWithoutInterval() {
        SseHeartbeatScheduler heartbeat = new SseHeartbeatScheduler(scheduler, heartbeatTask, Duration.ZERO);
        heartbeat.start();
        heartbeat.start();
        assertThat(heartbeat.isRunning()).isFalse();
        verify(scheduler, never()).scheduleAtFixedRate(any(Runnable.class), any(Duration.class));
        heartbeat.stop();
    }

    @Test
    void shouldRunTheTaskAndSwallowFailures() {
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        doReturn(future).when(scheduler).scheduleAtFixedRate(any(Runnable.class), eq(Duration.ofSeconds(5)));
        doThrow(new IllegalStateException("boom")).when(heartbeatTask).run();

        SseHeartbeatScheduler heartbeat = new SseHeartbeatScheduler(scheduler, heartbeatTask, Duration.ofSeconds(5));
        heartbeat.start();

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).scheduleAtFixedRate(captor.capture(), eq(Duration.ofSeconds(5)));
        captor.getValue().run();
        verify(heartbeatTask).run();
    }

    @Test
    void shouldSurviveSchedulerRejection() {
        when(scheduler.scheduleAtFixedRate(any(Runnable.class), any(Duration.class)))
                .thenThrow(new IllegalStateException("not initialized"));
        SseHeartbeatScheduler heartbeat = new SseHeartbeatScheduler(scheduler, heartbeatTask, Duration.ofSeconds(5));
        heartbeat.start();
        assertThat(heartbeat.isRunning()).isFalse();
    }
}
