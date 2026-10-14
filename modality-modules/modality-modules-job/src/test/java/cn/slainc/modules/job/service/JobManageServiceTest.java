package cn.slainc.modules.job.service;

import cn.slainc.core.exception.BizException;
import cn.slainc.job.annotation.Job;
import cn.slainc.job.spi.JobScheduler;
import cn.slainc.job.enums.MisfirePolicy;
import cn.slainc.job.support.JobContext;
import cn.slainc.job.support.JobDefinition;
import cn.slainc.job.support.JobHandler;
import cn.slainc.job.support.JobResult;
import cn.slainc.job.support.JobTriggerAck;
import cn.slainc.modules.job.autoconfigure.JobModuleProperties;
import cn.slainc.modules.job.entity.JobLogRecord;
import cn.slainc.modules.job.entity.JobTaskRecord;
import cn.slainc.modules.job.mapper.JobLogMapper;
import cn.slainc.modules.job.mapper.JobTaskMapper;
import cn.slainc.modules.job.store.NodeRegistry;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobManageServiceTest {

    private final JobTaskMapper jobTaskMapper = mock(JobTaskMapper.class);

    private final JobLogMapper jobLogMapper = mock(JobLogMapper.class);

    private final JobScheduler jobScheduler = mock(JobScheduler.class);

    private final NodeRegistry nodeRegistry = mock(NodeRegistry.class);

    private final Map<String, JobHandler> handlerBeans = Map.of("annotatedHandler", new AnnotatedHandler());

    @BeforeEach
    void stubUpdatedRowCount() {
        when(jobTaskMapper.updateById(any(JobTaskRecord.class))).thenReturn(1);
    }

    private JobManageService service() {
        return service(new JobModuleProperties());
    }

    private JobManageService service(JobModuleProperties properties) {
        when(nodeRegistry.nodeId()).thenReturn("node-1");
        return new JobManageService(jobTaskMapper, jobLogMapper, jobScheduler, nodeRegistry, properties,
                handlerBeans);
    }

    private JobTaskRecord job(Long id, String name, String handlerName, String cron, boolean enabled) {
        JobTaskRecord record = new JobTaskRecord();
        record.setId(id);
        record.setJobName(name);
        record.setHandlerName(handlerName);
        record.setCron(cron);
        record.setEnabled(enabled);
        record.setStatus(enabled ? JobManageService.STATUS_RUNNING : JobManageService.STATUS_PAUSED);
        record.setRetryCount(2);
        record.setRetryInterval(7);
        record.setTimeout(30);
        record.setConcurrent(false);
        record.setMisfirePolicy(MisfirePolicy.FIRE_ONCE_NOW.name());
        return record;
    }

    @Test
    void shouldIndexHandlersByJobAnnotationAndBeanName() {
        JobManageService target = service();

        assertThat(target.registeredHandlers())
                .containsKeys("annotated-job", "annotatedHandler")
                .hasSize(2);
    }

    @Test
    void shouldRegisterEnabledJobsOnStartup() {
        when(jobTaskMapper.selectList(any())).thenReturn(
                List.of(job(1L, "demo", "annotatedHandler", "0 0 3 * * ?", true)));
        AtomicReference<JobDefinition> captured = new AtomicReference<>();
        doAnswer(invocation -> {
            captured.set(invocation.getArgument(0));
            return null;
        }).when(jobScheduler).registerOnStartup(any());
        JobManageService target = service();

        assertThat(target.registerEnabledJobs()).isEqualTo(1);

        JobDefinition definition = captured.get();
        assertThat(definition).isNotNull();
        assertThat(definition.name()).isEqualTo("demo");
        assertThat(definition.cron()).isEqualTo("0 0 3 * * ?");
        assertThat(definition.retryCount()).isEqualTo(2);
        assertThat(definition.retryInterval()).isEqualTo(Duration.ofSeconds(7));
        assertThat(definition.timeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(definition.concurrent()).isFalse();
        assertThat(definition.misfirePolicy()).isEqualTo(MisfirePolicy.FIRE_ONCE_NOW);
    }

    @Test
    void shouldSkipJobsWithoutHandlerOnStartup() {
        when(jobTaskMapper.selectList(any())).thenReturn(List.of(job(1L, "demo", "missing", "0 0 3 * * ?", true)));
        JobManageService target = service();

        assertThat(target.registerEnabledJobs()).isZero();
        verify(jobScheduler, never()).registerOnStartup(any());
    }

    @Test
    void shouldSurviveStartupWhenTheDatasourceIsBroken() {
        when(jobTaskMapper.selectList(any())).thenThrow(new IllegalStateException("数据源不可用"));
        JobManageService target = service();

        assertThat(target.registerEnabledJobs()).isZero();
    }

    @Test
    void shouldRunAsApplicationRunner() {
        when(jobTaskMapper.selectList(any())).thenReturn(
                List.of(job(1L, "demo", "annotatedHandler", "0 0 3 * * ?", true)));
        JobManageService target = service();

        target.run(null);

        verify(jobScheduler).registerOnStartup(any(JobDefinition.class));
    }

    @Test
    void shouldClampPageSizeToConfiguredMaximum() {
        JobModuleProperties properties = new JobModuleProperties();
        properties.setMaxPageSize(50);
        JobManageService target = service(properties);
        AtomicReference<Page<JobTaskRecord>> captured = new AtomicReference<>();
        when(jobTaskMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            captured.set(invocation.getArgument(0));
            return invocation.getArgument(0);
        });

        target.page(1L, 10_000L, null, null);
        assertThat(captured.get().getSize()).isEqualTo(50L);

        target.page(0L, 0L, null, null);
        assertThat(captured.get().getCurrent()).isEqualTo(1L);
        assertThat(captured.get().getSize()).isEqualTo(1L);

        AtomicReference<Page<JobLogRecord>> logs = new AtomicReference<>();
        when(jobLogMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            logs.set(invocation.getArgument(0));
            return invocation.getArgument(0);
        });
        target.logs(1L, Long.MAX_VALUE, "demo");
        assertThat(logs.get().getSize()).isEqualTo(50L);
    }

    @Test
    void shouldNotMassAssignAuditFieldsOrStatusOnCreate() {
        JobManageService target = service();
        JobTaskRecord request = new JobTaskRecord();
        request.setId(99L);
        request.setJobName("demo");
        request.setHandlerName("annotatedHandler");
        request.setCron("0 0 3 * * ?");
        request.setEnabled(true);
        request.setStatus("HACKED");
        request.setCreateTime(0L);

        JobTaskRecord created = target.create(request);

        assertThat(created.getId()).isNull();
        assertThat(created.getStatus()).isEqualTo(JobManageService.STATUS_RUNNING);
        assertThat(created.getCreateTime()).isGreaterThan(0L);
        verify(jobTaskMapper).insert(created);
        verify(jobScheduler).register(any(JobDefinition.class));
    }

    @Test
    void shouldRejectInvalidCronAndMisfirePolicy() {
        JobManageService target = service();
        JobTaskRecord invalidCron = job(1L, "demo", "annotatedHandler", "not a cron", true);
        assertThatThrownBy(() -> target.create(invalidCron)).isInstanceOf(BizException.class);

        JobTaskRecord invalidPolicy = job(2L, "demo", "annotatedHandler", "0 0 3 * * ?", true);
        invalidPolicy.setMisfirePolicy("NOPE");
        assertThatThrownBy(() -> target.create(invalidPolicy)).isInstanceOf(BizException.class);
    }

    @Test
    void shouldKeepUpdateFromOverwritingAuditFields() {
        JobTaskRecord stored = job(1L, "demo", "annotatedHandler", "0 0 3 * * ?", true);
        stored.setCreateTime(1704067200000L);
        when(jobTaskMapper.selectById(1L)).thenReturn(stored);
        JobManageService target = service();
        JobTaskRecord request = job(1L, "renamed", "annotatedHandler", "0 0 4 * * ?", true);
        request.setStatus("HACKED");
        request.setCreateTime(0L);

        JobTaskRecord updated = target.update(request);

        assertThat(updated.getJobName()).isEqualTo("renamed");
        assertThat(updated.getStatus()).isEqualTo(JobManageService.STATUS_RUNNING);
        assertThat(updated.getCreateTime()).isEqualTo(1704067200000L);
        verify(jobScheduler).cancel("demo");
        verify(jobScheduler).register(any(JobDefinition.class));
    }

    @Test
    void shouldKeepChangeStatusIdempotent() {
        when(jobTaskMapper.selectById(1L)).thenReturn(job(1L, "demo", "annotatedHandler", "0 0 3 * * ?", true));
        JobManageService target = service();

        JobTaskRecord result = target.changeStatus(1L, true);

        assertThat(result.getStatus()).isEqualTo(JobManageService.STATUS_RUNNING);
        verify(jobTaskMapper, never()).updateById(any(JobTaskRecord.class));
        verify(jobScheduler, never()).register(any(JobDefinition.class));
        verify(jobScheduler, never()).cancel(anyString());
    }

    @Test
    void shouldPersistAndRescheduleWhenStatusChanges() {
        when(jobTaskMapper.selectById(1L)).thenReturn(job(1L, "demo", "annotatedHandler", "0 0 3 * * ?", true));
        JobManageService target = service();

        JobTaskRecord result = target.changeStatus(1L, false);

        assertThat(result.getStatus()).isEqualTo(JobManageService.STATUS_PAUSED);
        verify(jobTaskMapper).updateById(any(JobTaskRecord.class));
        verify(jobScheduler).cancel("demo");
    }

    @Test
    void shouldReturnTraceIdFromTrigger() {
        when(jobTaskMapper.selectById(1L)).thenReturn(job(1L, "demo", "annotatedHandler", "0 0 3 * * ?", true));
        when(jobScheduler.trigger(eq("demo"), any())).thenReturn(JobTriggerAck.accepted("trace-1", "demo"));
        JobManageService target = service();

        JobTriggerAck ack = target.trigger(1L, Map.of("k", "v"));

        assertThat(ack.accepted()).isTrue();
        assertThat(ack.traceId()).isEqualTo("trace-1");
    }

    @Test
    void shouldPurgeExpiredLogsAndDeleteByJob() {
        when(jobLogMapper.delete(any())).thenReturn(3);
        JobManageService target = service();

        assertThat(target.purgeExpiredLogs()).isEqualTo(3);
        assertThat(target.deleteLogsByJob("demo")).isEqualTo(3);

        JobModuleProperties disabled = new JobModuleProperties();
        disabled.setLogRetention(Duration.ZERO);
        assertThat(service(disabled).purgeExpiredLogs()).isZero();
        assertThatThrownBy(() -> target.deleteLogsByJob(" ")).isInstanceOf(BizException.class);
    }

    @Test
    void shouldReturnNullLastResultWhenNoRedisIsConfigured() {
        when(jobScheduler.lastResult("demo")).thenReturn(null);
        JobManageService target = service();

        assertThat(target.status("demo"))
                .containsEntry("jobName", "demo")
                .containsKey("nodes");
    }

    @Test
    void shouldPersistSkippedStatusForLockContention() {
        JobManageService target = service();

        target.record(new JobContext("demo", Map.of(), java.time.LocalDateTime.now(), 0),
                JobResult.skipped("任务正在其它节点执行", 0L));

        ArgumentCaptor<JobLogRecord> captor = ArgumentCaptor.forClass(JobLogRecord.class);
        verify(jobLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("SKIPPED");
        assertThat(captor.getValue().getSuccess()).isFalse();
    }

    @Test
    void shouldPersistSuccessAndFailureStatus() {
        JobManageService target = service();

        target.record(new JobContext("demo", Map.of(), java.time.LocalDateTime.now(), 1), JobResult.success(5L));
        target.record(new JobContext("demo", Map.of(), java.time.LocalDateTime.now(), 2),
                JobResult.failure("执行失败", 5L));

        ArgumentCaptor<JobLogRecord> captor = ArgumentCaptor.forClass(JobLogRecord.class);
        verify(jobLogMapper, times(2)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(JobLogRecord::getStatus)
                .containsExactly("SUCCESS", "FAILURE");
    }

    @Job(name = "annotated-job")
    static class AnnotatedHandler implements JobHandler {

        @Override
        public JobResult execute(JobContext context) {
            return JobResult.success(0L);
        }
    }
}
