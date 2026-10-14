package cn.slainc.job.api;

import cn.slainc.job.api.impl.JobSchedulerImpl;
import cn.slainc.job.autoconfigure.JobProperties;
import cn.slainc.job.enums.MisfirePolicy;
import cn.slainc.job.support.JobContext;
import cn.slainc.job.support.JobDefinition;
import cn.slainc.job.support.JobHandler;
import cn.slainc.job.support.JobLockSupport;
import cn.slainc.job.support.JobRecorder;
import cn.slainc.job.support.JobResult;
import cn.slainc.job.support.JobTriggerAck;
import cn.slainc.job.support.QuartzTestSupport;
import cn.slainc.redis.lock.DistributedLockTemplate;
import cn.slainc.redis.lock.LockHandle;
import cn.slainc.redis.lock.LockType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.quartz.CronTrigger;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.TriggerKey;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobSchedulerTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

    private final DistributedLockTemplate lockTemplate = mock(DistributedLockTemplate.class);

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);

    private JobScheduler scheduler;

    private Scheduler quartzScheduler;

    @AfterEach
    void tearDown() {
        if (scheduler != null) {
            scheduler.destroy();
        }
        shutdownQuartz();
    }

    private JobScheduler scheduler(boolean lockAcquired) {
        return scheduler(lockAcquired, QuartzTestSupport.ramScheduler());
    }

    private JobScheduler scheduler(boolean lockAcquired, Scheduler quartz) {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        // 必须先建好 LockHandle 桩，避免在 thenReturn 参数里嵌套 when()
        LockHandle handle = handle(lockAcquired);
        when(lockTemplate.acquire(anyString(), anyLong(), anyLong(), any(LockType.class))).thenReturn(handle);
        quartzScheduler = quartz;
        scheduler = new JobSchedulerImpl(redisTemplate, lockTemplate, new JobProperties(), quartz);
        startQuartz();
        return scheduler;
    }

    private JobScheduler lockFreeScheduler(Scheduler quartz) {
        quartzScheduler = quartz;
        scheduler = new JobSchedulerImpl(null, null, new JobProperties(), quartz);
        startQuartz();
        return scheduler;
    }

    private LockHandle handle(boolean locked) {
        LockHandle handle = mock(LockHandle.class);
        when(handle.isLocked()).thenReturn(locked);
        return handle;
    }

    private void startQuartz() {
        try {
            quartzScheduler.start();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void shutdownQuartz() {
        if (quartzScheduler == null) {
            return;
        }
        try {
            quartzScheduler.shutdown(true);
        } catch (Exception ignored) {
            // 关闭失败不影响断言
        }
        quartzScheduler = null;
    }

    @Test
    void shouldExecuteRegisteredJobAndRecordResult() {
        JobScheduler target = scheduler(true);
        AtomicInteger counter = new AtomicInteger();
        target.register(new JobDefinition("demo", null, null, 0, Duration.ZERO, context -> {
            counter.incrementAndGet();
            return JobResult.success(1L);
        }));

        JobResult result = target.triggerNow("demo", Map.of("k", "v"));
        assertThat(result.success()).isTrue();
        assertThat(counter.get()).isEqualTo(1);
        assertThat(target.definitions()).containsKey("demo");
    }

    @Test
    void shouldRetryUntilSuccess() {
        JobScheduler target = scheduler(true);
        AtomicInteger attempts = new AtomicInteger();
        target.register(new JobDefinition("retry", null, null, 2, Duration.ZERO, context -> {
            if (attempts.incrementAndGet() < 2) {
                throw new IllegalStateException("业务异常");
            }
            return JobResult.success(0L);
        }));

        assertThat(target.triggerNow("retry", Map.of()).success()).isTrue();
        assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    void shouldSkipWhenClusterLockNotAcquired() {
        JobScheduler target = scheduler(false);
        AtomicInteger counter = new AtomicInteger();
        target.register(new JobDefinition("locked", null, null, 0, Duration.ZERO, context -> {
            counter.incrementAndGet();
            return JobResult.success(0L);
        }));

        assertThat(target.triggerNow("locked", Map.of()).success()).isFalse();
        assertThat(counter.get()).isZero();
    }

    @Test
    void shouldAcquireOwnershipCheckedLockWithLease() {
        JobScheduler target = scheduler(true);
        target.register(new JobDefinition("leased", null, null, 0, Duration.ZERO,
                context -> JobResult.success(0L)));

        assertThat(target.triggerNow("leased", Map.of()).success()).isTrue();
        verify(lockTemplate).acquire(eq(JobLockSupport.lockName("leased")), eq(0L),
                eq(JobLockSupport.DEFAULT_LOCK_AT_MOST.toSeconds()), eq(LockType.REENTRANT));
        verify(lockTemplate, atLeastOnce()).acquire(anyString(), anyLong(), anyLong(), any(LockType.class));
    }

    @Test
    void shouldUseJobTimeoutAsLockAtMostFor() {
        JobScheduler target = scheduler(true);
        target.register(new JobDefinition("leased-timeout", null, null, 0, Duration.ZERO, Duration.ofSeconds(30),
                true, MisfirePolicy.SKIP, context -> JobResult.success(0L)));

        assertThat(target.triggerNow("leased-timeout", Map.of()).success()).isTrue();
        verify(lockTemplate).acquire(eq(JobLockSupport.lockName("leased-timeout")), eq(0L), eq(30L),
                eq(LockType.REENTRANT));
    }

    @Test
    void shouldReportSkippedWhenLockIsHeldByAnotherNode() {
        LockHandle notLocked = handle(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(lockTemplate.acquire(anyString(), anyLong(), anyLong(), any(LockType.class))).thenReturn(notLocked);
        JobRecorder recorder = mock(JobRecorder.class);
        quartzScheduler = QuartzTestSupport.ramScheduler();
        scheduler = new JobSchedulerImpl(redisTemplate, lockTemplate, new JobProperties(), quartzScheduler);
        scheduler.setRecorderProvider(recorderProvider(recorder));
        AtomicInteger counter = new AtomicInteger();
        scheduler.register(new JobDefinition("contended", null, null, 0, Duration.ZERO, context -> {
            counter.incrementAndGet();
            return JobResult.success(0L);
        }));

        JobResult result = scheduler.triggerNow("contended", Map.of());

        assertThat(result.skipped()).isTrue();
        assertThat(result.status()).isEqualTo(JobResult.Status.SKIPPED);
        assertThat(result.success()).isFalse();
        assertThat(counter.get()).isZero();
        verify(recorder).record(any(JobContext.class), argThat(JobResult::skipped));
    }

    @Test
    void shouldRemoveDefinitionAndFutureOnCancel() {
        JobScheduler target = scheduler(true);
        target.register(new JobDefinition("cron-job", "0 0 3 * * ?", null, 0, Duration.ZERO,
                context -> JobResult.success(0L)));
        assertThat(target.definitions()).containsKey("cron-job");

        target.cancel("cron-job");

        assertThat(target.definitions()).doesNotContainKey("cron-job");
        assertThatThrownBy(() -> target.triggerNow("cron-job", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        JobTriggerAck ack = target.trigger("cron-job", Map.of());
        assertThat(ack.accepted()).isFalse();
        assertThat(ack.message()).contains("任务不存在");
    }

    @Test
    void shouldSkipWhenPreviousExecutionIsStillRunning() throws Exception {
        JobScheduler target = scheduler(true);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger counter = new AtomicInteger();
        target.register(new JobDefinition("serial", null, null, 0, Duration.ZERO, null, false, MisfirePolicy.SKIP,
                context -> {
                    counter.incrementAndGet();
                    started.countDown();
                    try {
                        release.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return JobResult.success(1L);
                }));

        Thread runner = new Thread(() -> target.triggerNow("serial", Map.of()));
        runner.start();
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

        JobResult skipped = target.triggerNow("serial", Map.of());
        release.countDown();
        runner.join(5000L);

        assertThat(skipped.success()).isFalse();
        assertThat(skipped.message()).contains("上一次执行尚未结束");
        assertThat(counter.get()).isEqualTo(1);
        assertThat(target.runningJobs()).isEmpty();
    }

    @Test
    void shouldFailAndInterruptWhenTimeoutExceeded() {
        JobScheduler target = scheduler(true);
        target.register(new JobDefinition("slow", null, null, 0, Duration.ZERO, Duration.ofMillis(150), true,
                MisfirePolicy.SKIP, context -> {
                    try {
                        Thread.sleep(5000L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return JobResult.success(0L);
                }));

        long start = System.currentTimeMillis();
        JobResult result = target.triggerNow("slow", Map.of());
        long cost = System.currentTimeMillis() - start;

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("超时");
        assertThat(cost).isLessThan(3000L);
    }

    @Test
    void shouldTriggerAsynchronouslyAndPropagateTraceId() throws Exception {
        JobScheduler target = scheduler(true);
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> observed = new AtomicReference<>();
        target.register(new JobDefinition("async", null, null, 0, Duration.ZERO, context -> {
            observed.set(context.param(JobScheduler.TRACE_ID_PARAM));
            done.countDown();
            return JobResult.success(1L);
        }));

        JobTriggerAck ack = target.trigger("async", Map.of("biz", "1"));

        assertThat(ack.accepted()).isTrue();
        assertThat(ack.jobName()).isEqualTo("async");
        assertThat(ack.traceId()).isNotBlank();
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(observed.get()).isEqualTo(ack.traceId());
    }

    @Test
    void shouldApplyMisfirePolicyOnlyOnStartupRegistration() throws Exception {
        JobScheduler target = scheduler(true);
        CountDownLatch fired = new CountDownLatch(1);
        AtomicInteger counter = new AtomicInteger();
        JobDefinition definition = new JobDefinition("misfire", "0 0 3 * * ?", null, 0, Duration.ZERO, null, true,
                MisfirePolicy.FIRE_ONCE_NOW, context -> {
                    counter.incrementAndGet();
                    fired.countDown();
                    return JobResult.success(0L);
                });

        target.register(definition);
        assertThat(counter.get()).isZero();

        target.registerOnStartup(definition);
        assertThat(fired.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    void shouldNotRetryWhenRetryCountIsZero() {
        JobScheduler target = scheduler(true);
        AtomicInteger attempts = new AtomicInteger();
        target.register(new JobDefinition("no-retry", null, null, 0, Duration.ZERO, context -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("业务异常");
        }));

        JobResult result = target.triggerNow("no-retry", Map.of());

        assertThat(result.success()).isFalse();
        assertThat(result.status()).isEqualTo(JobResult.Status.FAILURE);
        assertThat(result.attempts()).isEqualTo(1);
        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    void shouldKeepAttemptCountInResultAfterRetries() {
        JobScheduler target = scheduler(true);
        AtomicInteger attempts = new AtomicInteger();
        target.register(new JobDefinition("always-fail", null, null, 2, Duration.ZERO, context -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("业务异常");
        }));

        JobResult result = target.triggerNow("always-fail", Map.of());

        assertThat(attempts.get()).isEqualTo(3);
        assertThat(result.attempts()).isEqualTo(3);
    }

    @Test
    void shouldStoreConvertedCronExpressionAndMisfireInstructionInQuartz() throws Exception {
        JobScheduler target = scheduler(true);
        target.register(new JobDefinition("cron-convert", "0 0 2 * * *", null, 0, Duration.ZERO, null, true,
                MisfirePolicy.SKIP, context -> JobResult.success(0L)));

        JobKey jobKey = JobKey.jobKey("cron-convert", JobSchedulerImpl.JOB_GROUP);
        CronTrigger trigger = (CronTrigger) quartzScheduler.getTrigger(
                TriggerKey.triggerKey("cron-convert", JobSchedulerImpl.JOB_GROUP));

        assertThat(quartzScheduler.checkExists(jobKey)).isTrue();
        assertThat(trigger).isNotNull();
        assertThat(trigger.getCronExpression()).isEqualTo("0 0 2 * * ?");
        assertThat(trigger.getMisfireInstruction()).isEqualTo(CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING);
    }

    @Test
    void shouldFireJobOnQuartzCronTrigger() throws Exception {
        JobScheduler target = scheduler(true);
        CountDownLatch fired = new CountDownLatch(1);
        target.register(new JobDefinition("tick", "* * * * * ?", null, 0, Duration.ZERO,
                context -> {
                    fired.countDown();
                    return JobResult.success(0L);
                }));

        assertThat(fired.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void shouldDeleteQuartzJobOnCancel() throws Exception {
        JobScheduler target = scheduler(true);
        target.register(new JobDefinition("cron-cancel", "0 0/5 * * * ?", null, 0, Duration.ZERO,
                context -> JobResult.success(0L)));
        JobKey jobKey = JobKey.jobKey("cron-cancel", JobSchedulerImpl.JOB_GROUP);
        assertThat(quartzScheduler.checkExists(jobKey)).isTrue();

        target.cancel("cron-cancel");

        assertThat(quartzScheduler.checkExists(jobKey)).isFalse();
        assertThat(target.definitions()).doesNotContainKey("cron-cancel");
    }

    @Test
    void shouldRejectCronThatCannotBeConverted() {
        JobScheduler target = scheduler(true);

        assertThatThrownBy(() -> target.register(new JobDefinition("bad-cron", "0 0 3 15 * MON", null, 0,
                Duration.ZERO, context -> JobResult.success(0L))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("day-of-month");
    }

    @Test
    void shouldRecordSkippedWhenQuartzSkipsAMisfire() throws Exception {
        JobRecorder recorder = mock(JobRecorder.class);
        quartzScheduler = QuartzTestSupport.ramScheduler(300L);
        scheduler = new JobSchedulerImpl(redisTemplate, lockTemplate, new JobProperties(), quartzScheduler);
        LockHandle handle = handle(true);
        when(lockTemplate.acquire(anyString(), anyLong(), anyLong(), any(LockType.class))).thenReturn(handle);
        scheduler.setRecorderProvider(recorderProvider(recorder));
        scheduler.register(new JobDefinition("misfire-skip", "* * * * * ?", null, 0, Duration.ZERO, null, true,
                MisfirePolicy.SKIP, context -> JobResult.success(0L)));

        Thread.sleep(2500L);
        startQuartz();

        verify(recorder, timeout(5000L).atLeastOnce())
                .record(any(JobContext.class), argThat(JobResult::skipped));
    }

    @Test
    void shouldRunMisfiredJobOnceWhenPolicyIsFireOnceNow() throws Exception {
        JobRecorder recorder = mock(JobRecorder.class);
        quartzScheduler = QuartzTestSupport.ramScheduler(300L);
        scheduler = new JobSchedulerImpl(redisTemplate, lockTemplate, new JobProperties(), quartzScheduler);
        LockHandle handle = handle(true);
        when(lockTemplate.acquire(anyString(), anyLong(), anyLong(), any(LockType.class))).thenReturn(handle);
        scheduler.setRecorderProvider(recorderProvider(recorder));
        CountDownLatch fired = new CountDownLatch(1);
        scheduler.register(new JobDefinition("misfire-catch-up", "* * * * * ?", null, 0, Duration.ZERO, null, true,
                MisfirePolicy.FIRE_ONCE_NOW, context -> {
                    fired.countDown();
                    return JobResult.success(0L);
                }));

        Thread.sleep(2500L);
        startQuartz();

        assertThat(fired.await(5, TimeUnit.SECONDS)).isTrue();
        verify(recorder, never()).record(any(JobContext.class), argThat(JobResult::skipped));
    }

    @Test
    void shouldFallBackToNoLockExecutionWithoutLockTemplate() {
        JobScheduler target = lockFreeScheduler(QuartzTestSupport.ramScheduler());
        AtomicInteger counter = new AtomicInteger();
        target.register(new JobDefinition("no-lock", null, null, 0, Duration.ZERO, context -> {
            counter.incrementAndGet();
            return JobResult.success(0L);
        }));

        assertThat(target.triggerNow("no-lock", Map.of()).success()).isTrue();
        assertThat(counter.get()).isEqualTo(1);
        verify(lockTemplate, never()).acquire(anyString(), anyLong(), anyLong(), any(LockType.class));
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<JobRecorder> recorderProvider(JobRecorder recorder) {
        ObjectProvider<JobRecorder> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(recorder);
        return provider;
    }
}
