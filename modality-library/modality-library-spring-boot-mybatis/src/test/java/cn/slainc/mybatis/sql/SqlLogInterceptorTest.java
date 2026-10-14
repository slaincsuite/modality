package cn.slainc.mybatis.sql;

import cn.slainc.core.constant.SystemConstants;
import cn.slainc.mybatis.base.User;
import cn.slainc.mybatis.properties.MybatisProperties;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SqlLogInterceptorTest {

    @Test
    @DisplayName("异步审计事件保留调用线程的 traceId")
    void asyncAuditEventKeepsCallerTraceId() throws Throwable {
        AtomicReference<SqlAuditEvent> captured = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        SqlLogInterceptor interceptor = interceptor(captured, latch, 1);

        try {
            MDC.put(SystemConstants.TRACE_ID_MDC_KEY, "trace-async-001");
            interceptor.intercept(updateInvocation(null));
        } finally {
            MDC.clear();
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS), "审计事件未在超时前发布");
        interceptor.shutdown();
        assertEquals("trace-async-001", captured.get().traceId());
    }

    @Test
    @DisplayName("执行失败时的审计事件同样保留调用线程的 traceId")
    void asyncAuditEventKeepsCallerTraceIdOnFailure() throws Throwable {
        AtomicReference<SqlAuditEvent> captured = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        SqlLogInterceptor interceptor = interceptor(captured, latch, 2);
        RuntimeException failure = new RuntimeException("模拟执行失败");

        try {
            MDC.put(SystemConstants.TRACE_ID_MDC_KEY, "trace-async-002");
            InvocationTargetException thrown = assertThrows(InvocationTargetException.class,
                    () -> interceptor.intercept(updateInvocation(failure)));
            assertEquals(failure, thrown.getCause());
        } finally {
            MDC.clear();
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS), "审计事件未在超时前发布");
        interceptor.shutdown();
        assertEquals("trace-async-002", captured.get().traceId());
        assertFalse(captured.get().success());
    }

    @Test
    @DisplayName("调用线程没有 traceId 时事件为空而不报错")
    void auditEventWithoutTraceIdStaysEmpty() throws Throwable {
        AtomicReference<SqlAuditEvent> captured = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        SqlLogInterceptor interceptor = interceptor(captured, latch, 1);

        interceptor.intercept(updateInvocation(null));

        assertTrue(latch.await(5, TimeUnit.SECONDS), "审计事件未在超时前发布");
        interceptor.shutdown();
        assertNull(captured.get().traceId());
    }

    private static SqlLogInterceptor interceptor(AtomicReference<SqlAuditEvent> captured, CountDownLatch latch,
                                                 int expectedEvents) {
        MybatisProperties properties = new MybatisProperties();
        properties.getSqlLog().setEnabled(true);
        properties.getSqlLog().setAudit(true);
        properties.getSqlLog().setAsync(true);
        properties.getSqlLog().setSlowThreshold(Long.MAX_VALUE);
        return new SqlLogInterceptor(properties, new SqlStatistics(20),
                event -> {
                    if (event instanceof SqlAuditEvent audit) {
                        captured.set(audit);
                        latch.countDown();
                    }
                });
    }

    private static Invocation updateInvocation(RuntimeException failure) throws Exception {
        MappedStatement statement = mock(MappedStatement.class);
        when(statement.getSqlCommandType()).thenReturn(SqlCommandType.INSERT);
        when(statement.getId()).thenReturn("cn.slainc.mybatis.dao.UserMapper.insert");
        Executor executor = mock(Executor.class);
        when(executor.update(any(MappedStatement.class), any())).thenAnswer(invocation -> {
            if (failure != null) {
                throw failure;
            }
            return 1;
        });
        Method method = Executor.class.getMethod("update", MappedStatement.class, Object.class);
        return new Invocation(executor, method, new Object[]{statement, new User()});
    }
}
