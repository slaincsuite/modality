package cn.slainc.changeaudit.aspect;

import cn.slainc.changeaudit.annotation.ChangeAudited;
import cn.slainc.changeaudit.api.impl.FieldDiffer;
import cn.slainc.changeaudit.autoconfigure.ChangeAuditConfigReader;
import cn.slainc.changeaudit.autoconfigure.ChangeAuditProperties;
import cn.slainc.changeaudit.enums.ChangeOperation;
import cn.slainc.changeaudit.event.AfterCommitExecutor;
import cn.slainc.changeaudit.spi.ChangeRecord;
import cn.slainc.changeaudit.spi.ChangeRecorder;
import cn.slainc.changeaudit.spi.SnapshotLoader;
import cn.slainc.changeaudit.support.SnapshotLoaderRegistry;
import cn.slainc.changeaudit.support.TestObjectProviders;
import cn.slainc.core.context.RequestContext;
import cn.slainc.core.context.TenantContext;
import cn.slainc.core.context.UserContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChangeAuditAspectTest {

    private ChangeAuditProperties properties;

    private ChangeAuditConfigReader reader;

    private ChangeRecorder recorder;

    private AfterCommitExecutor afterCommitExecutor;

    private ChangeAuditAspect aspect;

    private ProceedingJoinPoint point;

    private MethodSignature signature;

    private ChangeAudited audited;

    private final AtomicReference<Object> snapshot = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        properties = new ChangeAuditProperties();
        reader = new ChangeAuditConfigReader(properties, TestObjectProviders.empty());
        recorder = mock(ChangeRecorder.class);
        afterCommitExecutor = mock(AfterCommitExecutor.class);
        SnapshotLoaderRegistry registry = new SnapshotLoaderRegistry(List.of(new SnapshotLoader() {
            @Override
            public String type() {
                return "DEMO";
            }

            @Override
            public Object load(String id) {
                return snapshot.getAndSet(null);
            }
        }));
        FieldDiffer differ = new FieldDiffer(JsonMapper.builder().build(), reader);
        aspect = new ChangeAuditAspect(reader, registry, differ, recorder, afterCommitExecutor);

        point = mock(ProceedingJoinPoint.class);
        signature = mock(MethodSignature.class);
        audited = mock(ChangeAudited.class);
        when(point.getSignature()).thenReturn(signature);
        when(point.getTarget()).thenReturn(this);
        when(audited.type()).thenReturn("DEMO");
        when(audited.operation()).thenReturn(ChangeOperation.AUTO);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
        TenantContext.clear();
        RequestContext.clear();
    }

    @Test
    void collectsBeforeAndAfterAndWritesOnlyAfterCommit() throws Throwable {
        UserContext.set(UserContext.UserInfo.of(11L, "tester", null));
        TenantContext.set(22L);
        RequestContext.set(new RequestContext.RequestInfo("trace-1", null, null, "PUT",
                "/open-api/v1/infra/tenant"));
        snapshot.set(demo(7L, "old"));
        Demo updated = demo(7L, "new");
        when(signature.getMethod()).thenReturn(method("update"));
        when(point.getArgs()).thenReturn(new Object[]{updated});
        when(point.proceed()).thenReturn(updated);
        when(audited.id()).thenReturn("#entity.id");
        when(audited.name()).thenReturn("#entity.name");

        Object result = aspect.around(point, audited);

        assertThat(result).isSameAs(updated);
        ArgumentCaptor<Runnable> actionCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(afterCommitExecutor).afterCommit(actionCaptor.capture());
        verify(recorder, never()).record(any());

        actionCaptor.getValue().run();

        ArgumentCaptor<ChangeRecord> captor = ArgumentCaptor.forClass(ChangeRecord.class);
        verify(recorder).record(captor.capture());
        ChangeRecord change = captor.getValue();
        assertThat(change.entityType()).isEqualTo("DEMO");
        assertThat(change.entityId()).isEqualTo("7");
        assertThat(change.entityName()).isEqualTo("new");
        assertThat(change.operation()).isEqualTo("UPDATE");
        assertThat(change.changedFields()).isEqualTo(1);
        assertThat(change.changes()).contains("\"field\":\"name\"").contains("old").contains("new");
        assertThat(change.operator()).isEqualTo("tester");
        assertThat(change.operatorId()).isEqualTo(11L);
        assertThat(change.tenantId()).isEqualTo(22L);
        assertThat(change.requestUri()).isEqualTo("/open-api/v1/infra/tenant");
        assertThat(change.traceId()).isEqualTo("trace-1");
        assertThat(change.changeSource()).isEqualTo("API");
        assertThat(change.changeTime()).isNotNull();
    }

    @Test
    void fallsBackToTheEntityIdWhenTheExpressionCannotBeResolved() throws Throwable {
        snapshot.set(demo(7L, "old"));
        Demo updated = demo(7L, "new");
        when(signature.getMethod()).thenReturn(method("update"));
        when(point.getArgs()).thenReturn(new Object[]{updated});
        when(point.proceed()).thenReturn(updated);
        when(audited.id()).thenReturn("#id");

        aspect.around(point, audited);

        ArgumentCaptor<ChangeRecord> captor = captureRecorded();
        assertThat(captor.getValue().entityId()).isEqualTo("7");
        assertThat(captor.getValue().entityName()).isEqualTo("new");
        assertThat(captor.getValue().changeSource()).isEqualTo("SYSTEM");
    }

    @Test
    void usesTheEntityArgumentWhenTheResultIsNotAnEntity() throws Throwable {
        snapshot.set(demo(7L, "old"));
        Demo updated = demo(7L, "new");
        when(signature.getMethod()).thenReturn(method("changeStatus"));
        when(point.getArgs()).thenReturn(new Object[]{updated});
        when(point.proceed()).thenReturn(1);
        when(audited.id()).thenReturn("#p0.id");

        aspect.around(point, audited);

        ArgumentCaptor<ChangeRecord> captor = captureRecorded();
        assertThat(captor.getValue().entityId()).isEqualTo("7");
        assertThat(captor.getValue().operation()).isEqualTo("UPDATE");
        assertThat(captor.getValue().changedFields()).isEqualTo(1);
    }

    @Test
    void infersInsertWhenThereIsNoSnapshot() throws Throwable {
        snapshot.set(null);
        Demo created = demo(8L, "created");
        when(signature.getMethod()).thenReturn(method("create"));
        when(point.getArgs()).thenReturn(new Object[]{created});
        when(point.proceed()).thenReturn(created);
        when(audited.id()).thenReturn("#entity.id");

        aspect.around(point, audited);

        ArgumentCaptor<ChangeRecord> captor = captureRecorded();
        assertThat(captor.getValue().entityId()).isEqualTo("8");
        assertThat(captor.getValue().operation()).isEqualTo("INSERT");
        assertThat(captor.getValue().changes()).contains("\"before\":null");
    }

    @Test
    void infersDeleteFromTheMethodName() throws Throwable {
        snapshot.set(demo(7L, "old"));
        when(signature.getMethod()).thenReturn(method("delete"));
        when(point.getArgs()).thenReturn(new Object[]{7L});
        when(point.proceed()).thenReturn(1);
        when(audited.id()).thenReturn("#id");

        aspect.around(point, audited);

        ArgumentCaptor<ChangeRecord> captor = captureRecorded();
        assertThat(captor.getValue().entityId()).isEqualTo("7");
        assertThat(captor.getValue().operation()).isEqualTo("DELETE");
        assertThat(captor.getValue().changedFields()).isGreaterThan(0);
    }

    @Test
    void honoursAnExplicitOperation() throws Throwable {
        snapshot.set(demo(7L, "old"));
        Demo updated = demo(7L, "new");
        when(signature.getMethod()).thenReturn(method("update"));
        when(point.getArgs()).thenReturn(new Object[]{updated});
        when(point.proceed()).thenReturn(updated);
        when(audited.id()).thenReturn("#entity.id");
        when(audited.operation()).thenReturn(ChangeOperation.DELETE);

        aspect.around(point, audited);

        assertThat(captureRecorded().getValue().operation()).isEqualTo("DELETE");
    }

    @Test
    void skipsTheAuditWhenNothingChanged() throws Throwable {
        snapshot.set(demo(7L, "same"));
        Demo unchanged = demo(7L, "same");
        when(signature.getMethod()).thenReturn(method("update"));
        when(point.getArgs()).thenReturn(new Object[]{unchanged});
        when(point.proceed()).thenReturn(unchanged);
        when(audited.id()).thenReturn("#entity.id");

        aspect.around(point, audited);

        verify(afterCommitExecutor, never()).afterCommit(any());
        verify(recorder, never()).record(any());
    }

    @Test
    void recordsNoChangeWhenConfigured() throws Throwable {
        properties.setRecordNoChange(true);
        snapshot.set(demo(7L, "same"));
        Demo unchanged = demo(7L, "same");
        when(signature.getMethod()).thenReturn(method("update"));
        when(point.getArgs()).thenReturn(new Object[]{unchanged});
        when(point.proceed()).thenReturn(unchanged);
        when(audited.id()).thenReturn("#entity.id");

        aspect.around(point, audited);

        ArgumentCaptor<ChangeRecord> captor = captureRecorded();
        assertThat(captor.getValue().changedFields()).isZero();
        assertThat(captor.getValue().changes()).isEqualTo("[]");
    }

    @Test
    void skipsTheAuditWhenDisabled() throws Throwable {
        properties.setEnabled(false);
        snapshot.set(demo(7L, "old"));
        Demo updated = demo(7L, "new");
        when(point.getArgs()).thenReturn(new Object[]{updated});
        when(point.proceed()).thenReturn(updated);

        assertThat(aspect.around(point, audited)).isSameAs(updated);
        verify(afterCommitExecutor, never()).afterCommit(any());
    }

    @Test
    void doesNotAuditAFailedInvocation() throws Throwable {
        snapshot.set(demo(7L, "old"));
        when(signature.getMethod()).thenReturn(method("update"));
        when(point.getArgs()).thenReturn(new Object[]{demo(7L, "new")});
        when(point.proceed()).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> aspect.around(point, audited)).isInstanceOf(IllegalStateException.class);
        verify(afterCommitExecutor, never()).afterCommit(any());
    }

    private ArgumentCaptor<ChangeRecord> captureRecorded() {
        ArgumentCaptor<Runnable> actionCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(afterCommitExecutor).afterCommit(actionCaptor.capture());
        actionCaptor.getValue().run();
        ArgumentCaptor<ChangeRecord> captor = ArgumentCaptor.forClass(ChangeRecord.class);
        verify(recorder).record(captor.capture());
        return captor;
    }

    private static Method method(String name) throws NoSuchMethodException {
        for (Method candidate : SampleService.class.getDeclaredMethods()) {
            if (candidate.getName().equals(name)) {
                return candidate;
            }
        }
        throw new NoSuchMethodException(name);
    }

    private static Demo demo(Long id, String name) {
        Demo demo = new Demo();
        demo.setId(id);
        demo.setName(name);
        return demo;
    }

    static class SampleService {

        Demo update(Demo entity) {
            return entity;
        }

        int changeStatus(Demo entity) {
            return 1;
        }

        Demo create(Demo entity) {
            return entity;
        }

        int delete(Long id) {
            return 1;
        }
    }

    static class Demo {

        private Long id;

        private String name;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
