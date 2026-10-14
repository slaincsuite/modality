package cn.slainc.job.support;

import cn.slainc.job.api.JobScheduler;
import cn.slainc.job.autoconfigure.JobProperties;
import cn.slainc.job.enums.MisfirePolicy;
import cn.slainc.job.support.JobContext;
import cn.slainc.job.support.JobDefinition;
import cn.slainc.job.support.JobLockSupport;
import cn.slainc.job.support.JobRecorder;
import cn.slainc.job.support.JobResult;
import cn.slainc.job.support.JobTriggerAck;
import cn.slainc.job.support.QuartzCronSupport;
import cn.slainc.job.support.QuartzMisfireSupport;
import cn.slainc.job.support.RetryBackoff;
import cn.slainc.redis.lock.DistributedLockTemplate;
import cn.slainc.redis.lock.LockHandle;
import cn.slainc.redis.lock.LockType;
import io.github.resilience4j.core.functions.CheckedSupplier;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.quartz.TriggerListener;
import org.quartz.impl.matchers.GroupMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public final class JobSchedulerImpl implements JobScheduler, DisposableBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(JobScheduler.class);

    public static final String JOB_GROUP = "modality-job";

    public static final String JOB_DATA_JOB_NAME = "jobName";

    public static final String SCHEDULER_CONTEXT_KEY = "modality.job.scheduler";

    private static final Duration STARTUP_COMPENSATION_LEASE = Duration.ofMinutes(30);

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final StringRedisTemplate redisTemplate;

    private final DistributedLockTemplate lockTemplate;

    private final JobProperties properties;

    private final Scheduler quartzScheduler;

    private final ThreadPoolTaskExecutor workerExecutor;

    private final ExecutorService driverExecutor;

    private final Map<String, JobDefinition> definitions = new ConcurrentHashMap<>();

    private final Set<String> running = ConcurrentHashMap.newKeySet();

    // 无 Redis 锁时的进程内补偿标记，保证 FIRE_ONCE_NOW 在本进程只补一次
    private final Set<String> compensatedLocally = ConcurrentHashMap.newKeySet();

    private ObjectProvider<JobRecorder> recorderProvider;

    public JobSchedulerImpl(StringRedisTemplate redisTemplate, DistributedLockTemplate lockTemplate,
                            JobProperties properties, Scheduler quartzScheduler) {
        if (quartzScheduler == null) {
            throw new IllegalArgumentException("调度任务必须有 Quartz Scheduler Bean");
        }
        this.redisTemplate = redisTemplate;
        this.lockTemplate = lockTemplate;
        this.properties = properties;
        this.quartzScheduler = quartzScheduler;
        this.workerExecutor = new ThreadPoolTaskExecutor();
        this.workerExecutor.setCorePoolSize(properties.getPoolSize());
        this.workerExecutor.setMaxPoolSize(properties.getPoolSize());
        this.workerExecutor.setQueueCapacity(properties.getQueueCapacity());
        this.workerExecutor.setThreadFactory(Thread.ofPlatform().daemon().name("modality-job-worker-", 0L).factory());
        this.workerExecutor.initialize();
        this.driverExecutor = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("modality-job-driver-", 0L).factory());
        bindSchedulerContext();
    }

    private void bindSchedulerContext() {
        try {
            quartzScheduler.getContext().put(SCHEDULER_CONTEXT_KEY, this);
            quartzScheduler.getListenerManager().addTriggerListener(new MisfireListener(),
                    GroupMatcher.triggerGroupEquals(JOB_GROUP));
        } catch (SchedulerException e) {
            throw new IllegalStateException("绑定任务调度器到 Quartz 失败", e);
        }
    }

    @Override
    public void setRecorderProvider(ObjectProvider<JobRecorder> recorderProvider) {
        this.recorderProvider = recorderProvider;
    }

    @Override
    public void register(JobDefinition definition) {
        register(definition, false);
    }

    @Override
    public void registerOnStartup(JobDefinition definition) {
        register(definition, true);
    }

    private void register(JobDefinition definition, boolean startup) {
        if (definition == null || definition.name() == null || definition.name().isBlank()) {
            return;
        }
        definitions.put(definition.name(), definition);
        syncQuartz(definition);
        if (startup && needsStartupCompensation(definition)) {
            fireStartupCompensationOnce(definition);
        }
    }

    private void syncQuartz(JobDefinition definition) {
        JobKey jobKey = jobKey(definition.name());
        JobDetail jobDetail = JobBuilder.newJob(QuartzJobBridge.class)
                .withIdentity(jobKey)
                .withDescription(definition.description() == null || definition.description().isBlank()
                        ? definition.name() : definition.description())
                .usingJobData(JOB_DATA_JOB_NAME, definition.name())
                .storeDurably()
                .build();
        try {
            if (!definition.hasCron()) {
                quartzScheduler.deleteJob(jobKey);
                quartzScheduler.addJob(jobDetail, true);
                return;
            }
            String quartzCron = QuartzCronSupport.toQuartz(definition.cron());
            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(triggerKey(definition.name()))
                    .forJob(jobKey)
                    .withSchedule(QuartzMisfireSupport.scheduleBuilder(quartzCron, definition.misfirePolicy()))
                    .build();
            quartzScheduler.scheduleJob(jobDetail, Set.of(trigger), true);
            LOGGER.info("任务已调度 name={} cron={} quartzCron={} misfire={}",
                    definition.name(), definition.cron(), quartzCron, definition.misfirePolicy());
        } catch (SchedulerException e) {
            throw new IllegalStateException("调度任务失败 name=" + definition.name(), e);
        }
    }

    @Override
    public Map<String, JobDefinition> definitions() {
        return Map.copyOf(definitions);
    }

    @Override
    public Set<String> runningJobs() {
        return Set.copyOf(running);
    }

    @Override
    public JobTriggerAck trigger(String name, Map<String, Object> params) {
        JobDefinition definition = definitions.get(name);
        String traceId = newTraceId();
        if (definition == null) {
            return JobTriggerAck.rejected(traceId, name, "任务不存在：" + name);
        }
        Map<String, Object> executionParams = withTraceId(params, traceId);
        try {
            driverExecutor.execute(() -> execute(definition, executionParams));
        } catch (RejectedExecutionException e) {
            LOGGER.warn("任务触发被拒绝 traceId={} name={}", traceId, name, e);
            return JobTriggerAck.rejected(traceId, name, "任务执行器拒绝了本次提交");
        }
        return JobTriggerAck.accepted(traceId, name);
    }

    @Override
    public JobResult triggerNow(String name, Map<String, Object> params) {
        JobDefinition definition = definitions.get(name);
        if (definition == null) {
            throw new IllegalArgumentException("任务不存在：" + name);
        }
        return execute(definition, withTraceId(params, newTraceId()));
    }

    @Override
    public void cancel(String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        try {
            quartzScheduler.deleteJob(jobKey(name));
        } catch (SchedulerException e) {
            LOGGER.error("删除 Quartz 任务失败 name={}", name, e);
        }
        if (definitions.remove(name) != null) {
            LOGGER.info("任务已取消 name={}", name);
        }
    }

    public void executeScheduled(String name) {
        JobDefinition definition = definitions.get(name);
        if (definition == null) {
            LOGGER.warn("触发被忽略，任务定义未注册 name={}", name);
            return;
        }
        execute(definition, withTraceId(Map.of(), newTraceId()));
    }

    @Override
    public JobResult execute(JobDefinition definition, Map<String, Object> params) {
        String traceId = param(params, TRACE_ID_PARAM);
        boolean tracked = !definition.concurrent();
        if (tracked && !running.add(definition.name())) {
            LOGGER.info("任务跳过，上一次执行尚未结束 traceId={} name={}", traceId, definition.name());
            return JobResult.failure("上一次执行尚未结束", 0L);
        }
        try {
            return executeWithLock(definition, params, traceId);
        } finally {
            if (tracked) {
                running.remove(definition.name());
            }
        }
    }

    private JobResult executeWithLock(JobDefinition definition, Map<String, Object> params, String traceId) {
        if (lockTemplate == null) {
            // 没有 Redis 锁时退化为单进程执行，集群互斥由 Quartz 集群存储承担
            return runWithTimeout(definition, params, traceId);
        }
        String lockKey = JobLockSupport.lockName(definition.name());
        long leaseSeconds = JobLockSupport.lockAtMostForSeconds(definition, properties.getLockTtl());
        LockHandle handle;
        try {
            handle = lockTemplate.acquire(lockKey, 0L, leaseSeconds, LockType.REENTRANT);
        } catch (RuntimeException e) {
            LOGGER.warn("获取任务分布式锁失败 traceId={} name={} lock={}", traceId, definition.name(), lockKey, e);
            return JobResult.failure("获取任务分布式锁失败：" + e.getMessage(), 0L);
        }
        if (!handle.isLocked()) {
            LOGGER.info("任务跳过，锁被其它节点持有 traceId={} name={} lock={}",
                    traceId, definition.name(), lockKey);
            return skippedByLock(definition, params, traceId, "任务正在其它节点执行");
        }
        try {
            return runWithTimeout(definition, params, traceId);
        } finally {
            try {
                handle.close();
            } catch (RuntimeException e) {
                LOGGER.warn("释放任务分布式锁失败 traceId={} name={} lock={}", traceId, definition.name(), lockKey, e);
            }
        }
    }

    private JobResult skippedByLock(JobDefinition definition, Map<String, Object> params, String traceId,
                                    String message) {
        JobResult skipped = JobResult.skipped(message, 0L);
        record(new JobContext(definition.name(), params, LocalDateTime.now(), 0, definition.tenantId()), skipped,
                definition, traceId);
        return skipped;
    }

    private JobResult runWithTimeout(JobDefinition definition, Map<String, Object> params, String traceId) {
        if (!definition.hasTimeout()) {
            return runWithRetry(definition, params, traceId);
        }
        Duration timeout = definition.timeout();
        LocalDateTime fireTime = LocalDateTime.now();
        Future<JobResult> future;
        try {
            future = workerExecutor.submit(() -> runWithRetry(definition, params, traceId));
        } catch (RejectedExecutionException e) {
            LOGGER.warn("任务被工作线程池拒绝 traceId={} name={}", traceId, definition.name(), e);
            return JobResult.failure("任务工作线程池已饱和", 0L);
        }
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            JobResult timeoutResult = JobResult.failure(
                    String.format("任务执行超时（%d 毫秒）", timeout.toMillis()), timeout.toMillis());
            LOGGER.warn("任务超时 traceId={} name={} timeout={}ms", traceId, definition.name(), timeout.toMillis());
            record(new JobContext(definition.name(), params, fireTime, 1, definition.tenantId()), timeoutResult,
                    definition, traceId);
            return timeoutResult;
        } catch (InterruptedException e) {
            // 按降级路径处理并恢复中断标记
            future.cancel(true);
            Thread.currentThread().interrupt();
            return JobResult.failure("任务执行被中断", 0L);
        } catch (ExecutionException e) {
            LOGGER.error("任务执行异常 traceId={} name={}", traceId, definition.name(), e.getCause());
            return JobResult.failure(String.valueOf(e.getCause() == null ? e.getMessage() : e.getCause().getMessage()), 0L);
        }
    }

    private JobResult runWithRetry(JobDefinition definition, Map<String, Object> params, String traceId) {
        AtomicInteger attempts = new AtomicInteger();
        Retry retry = retryOf(definition, traceId);
        CheckedSupplier<JobResult> supplier =
                Retry.decorateCheckedSupplier(retry, () -> attemptOnce(definition, params, traceId, attempts));
        try {
            JobResult result = supplier.get();
            return result == null ? JobResult.success(0L) : result;
        } catch (Throwable e) {
            if (e instanceof Error error) {
                throw error;
            }
            LOGGER.warn("任务重试中止 traceId={} name={} attempts={}", traceId, definition.name(),
                    attempts.get(), e);
            return JobResult.failure(e.getMessage(), 0L).withAttempts(attempts.get());
        }
    }

    private JobResult attemptOnce(JobDefinition definition, Map<String, Object> params, String traceId,
                                  AtomicInteger attempts) {
        int attempt = attempts.incrementAndGet();
        long start = System.currentTimeMillis();
        JobContext context = new JobContext(definition.name(), params, LocalDateTime.now(), attempt,
                definition.tenantId());
        JobResult result;
        try {
            result = definition.handler().execute(context);
            if (result == null) {
                result = JobResult.success(System.currentTimeMillis() - start);
            }
        } catch (RuntimeException e) {
            LOGGER.warn("任务执行失败 traceId={} name={} attempt={}", traceId, definition.name(), attempt, e);
            result = JobResult.failure(e.getMessage(), System.currentTimeMillis() - start);
        }
        result = result.withAttempts(attempt);
        record(context, result, definition, traceId);
        return result;
    }

    private Retry retryOf(JobDefinition definition, String traceId) {
        JobProperties.Backoff backoff = properties.getRetry().getBackoff();
        RetryConfig config = RetryConfig.<JobResult>custom()
                .maxAttempts(definition.retryCount() + 1)
                .intervalFunction(RetryBackoff.of(definition.retryInterval(), backoff.getMultiplier(),
                        backoff.getMaxInterval()))
                .retryOnResult(result -> !result.success() && !result.skipped())
                .failAfterMaxAttempts(false)
                .build();
        Retry retry = Retry.of("modality-job:" + definition.name(), config);
        retry.getEventPublisher().onRetry(event -> LOGGER.warn(
                "任务将重试 traceId={} name={} failedAttempt={} nextAttempt={} backoff={}ms",
                traceId, definition.name(), event.getNumberOfRetryAttempts(),
                event.getNumberOfRetryAttempts() + 1, event.getWaitInterval().toMillis()));
        return retry;
    }

    private void record(JobContext context, JobResult result, JobDefinition definition, String traceId) {
        try {
            if (redisTemplate != null) {
                redisTemplate.opsForValue().set(properties.getKeyPrefix() + ":last:" + definition.name(),
                        JSON.writeValueAsString(result), properties.getResultTtl());
            }
        } catch (RuntimeException e) {
            // 最近结果缓存失败不能影响执行日志落库
            LOGGER.warn("缓存任务最近结果失败 traceId={} name={}", traceId, definition.name(), e);
        }
        try {
            JobRecorder recorder = recorderProvider == null ? null : recorderProvider.getIfAvailable();
            if (recorder != null) {
                recorder.record(context, result);
            }
        } catch (RuntimeException e) {
            LOGGER.warn("记录任务结果失败 traceId={} name={}", traceId, definition.name(), e);
        }
        if (result.skipped()) {
            LOGGER.info("任务跳过 traceId={} name={} message={}", traceId, definition.name(), result.message());
        } else if (result.success()) {
            LOGGER.info("任务完成 traceId={} name={} attempt={} cost={}ms",
                    traceId, definition.name(), context.attempt(), result.costMillis());
        } else {
            LOGGER.warn("任务失败 traceId={} name={} attempt={} message={}",
                    traceId, definition.name(), context.attempt(), result.message());
        }
    }

    @Override
    public JobResult lastResult(String name) {
        if (redisTemplate == null) {
            return null;
        }
        try {
            String json = redisTemplate.opsForValue().get(properties.getKeyPrefix() + ":last:" + name);
            return json == null ? null : JSON.readValue(json, JobResult.class);
        } catch (RuntimeException e) {
            LOGGER.warn("读取任务最近结果失败 name={}", name, e);
            return null;
        }
    }

    private boolean needsStartupCompensation(JobDefinition definition) {
        return definition.hasCron() && definition.misfirePolicy() == MisfirePolicy.FIRE_ONCE_NOW && isRamJobStore();
    }

    private boolean isRamJobStore() {
        try {
            return quartzScheduler.getMetaData().getJobStoreClass().getName().contains("RAMJobStore");
        } catch (SchedulerException e) {
            LOGGER.warn("读取 Quartz 存储元数据失败", e);
            return false;
        }
    }

    private void fireStartupCompensationOnce(JobDefinition definition) {
        if (!compensatedLocally.add(definition.name())) {
            return;
        }
        if (lockTemplate != null) {
            String compensationKey = JobLockSupport.startupCompensationLockName(definition.name());
            boolean acquired;
            try {
                // 补偿锁到期自动释放，保证集群内一次停机窗口只补偿一次
                acquired = lockTemplate
                        .acquire(compensationKey, 0L, STARTUP_COMPENSATION_LEASE.toSeconds(), LockType.REENTRANT)
                        .isLocked();
            } catch (RuntimeException e) {
                LOGGER.warn("启动补偿锁获取失败 name={} reason={}", definition.name(), e.getMessage());
                return;
            }
            if (!acquired) {
                LOGGER.info("启动补偿已由其它节点触发 name={}", definition.name());
                return;
            }
        }
        LOGGER.info("错过触发策略为 FIRE_ONCE_NOW，启动后补跑一次 name={}", definition.name());
        dispatch(definition);
    }

    private void dispatch(JobDefinition definition) {
        String traceId = newTraceId();
        Map<String, Object> params = withTraceId(Map.of(), traceId);
        try {
            driverExecutor.execute(() -> execute(definition, params));
        } catch (RejectedExecutionException e) {
            LOGGER.warn("调度任务提交被拒绝 traceId={} name={}", traceId, definition.name(), e);
        }
    }

    private JobKey jobKey(String name) {
        return JobKey.jobKey(name, JOB_GROUP);
    }

    private TriggerKey triggerKey(String name) {
        return TriggerKey.triggerKey(name, JOB_GROUP);
    }

    private Map<String, Object> withTraceId(Map<String, Object> params, String traceId) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (params != null) {
            result.putAll(params);
        }
        result.putIfAbsent(TRACE_ID_PARAM, traceId);
        return result;
    }

    private String param(Map<String, Object> params, String key) {
        Object value = params == null ? null : params.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    @Override
    public void destroy() {
        definitions.clear();
        driverExecutor.shutdown();
        try {
            if (!driverExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                driverExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            // 关闭被中断则强制退出并恢复中断标记
            Thread.currentThread().interrupt();
            driverExecutor.shutdownNow();
        }
        workerExecutor.destroy();
    }

    private final class MisfireListener implements TriggerListener {

        @Override
        public String getName() {
            return "modality-job-misfire";
        }

        @Override
        public void triggerFired(Trigger trigger, org.quartz.JobExecutionContext context) {
        }

        @Override
        public boolean vetoJobExecution(Trigger trigger, org.quartz.JobExecutionContext context) {
            return false;
        }

        @Override
        public void triggerMisfired(Trigger trigger) {
            if (trigger.getMisfireInstruction() != CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING) {
                return;
            }
            JobDefinition definition = definitions.get(trigger.getKey().getName());
            if (definition == null) {
                return;
            }
            String traceId = newTraceId();
            LOGGER.info("任务错过触发并被策略跳过 traceId={} name={}", traceId, definition.name());
            record(new JobContext(definition.name(), Map.of(), LocalDateTime.now(), 0, definition.tenantId()),
                    JobResult.skipped("错过触发，按 misfire 策略跳过", 0L), definition, traceId);
        }

        @Override
        public void triggerComplete(Trigger trigger, org.quartz.JobExecutionContext context,
                                    Trigger.CompletedExecutionInstruction instruction) {
        }
    }
}
