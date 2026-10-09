package cn.slainc.datasource.routing;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TransactionMasterAspectTest {

    @AfterEach
    void clearContext() {
        while (DynamicDataSourceContextHolder.peek() != null) {
            DynamicDataSourceContextHolder.poll();
        }
    }

    @Test
    @DisplayName("事务强制主库取运行期当前主库")
    void transactionPushesCurrentPrimary() throws Throwable {
        RoutingDataSource routing = mock(RoutingDataSource.class);
        when(routing.primaryName()).thenReturn("master");
        TransactionMasterAspect aspect = new TransactionMasterAspect(routing, "master");

        assertEquals("master", pushedPrimary(aspect));
    }

    @Test
    @DisplayName("故障切换后事务改走新主库而不是启动时的旧主库")
    void failoverChangesTransactionTarget() throws Throwable {
        RoutingDataSource routing = mock(RoutingDataSource.class);
        when(routing.primaryName()).thenReturn("master");
        TransactionMasterAspect aspect = new TransactionMasterAspect(routing, "master");
        assertEquals("master", pushedPrimary(aspect));

        when(routing.primaryName()).thenReturn("report");

        assertEquals("report", pushedPrimary(aspect));
        assertNull(DynamicDataSourceContextHolder.peek());
    }

    @Test
    @DisplayName("主库名取不到时退回配置名")
    void blankPrimaryFallsBackToConfiguredName() throws Throwable {
        RoutingDataSource routing = mock(RoutingDataSource.class);
        when(routing.primaryName()).thenReturn(" ");

        assertEquals("master", pushedPrimary(new TransactionMasterAspect(routing, "master")));
        assertEquals("config-master", pushedPrimary(new TransactionMasterAspect("config-master")));
    }

    @Test
    @DisplayName("存在 @DS 时事务不强制主库")
    void explicitDataSourceSkipsMasterForcing() throws Throwable {
        RoutingDataSource routing = mock(RoutingDataSource.class);
        when(routing.primaryName()).thenReturn("master");
        TransactionMasterAspect aspect = new TransactionMasterAspect(routing, "master");
        AtomicReference<String> duringProceed = new AtomicReference<>("unset");

        Object result = aspect.forceMaster(point("explicitDataSourceSample", ignored ->
                duringProceed.set(DynamicDataSourceContextHolder.peek())));

        assertEquals("ok", result);
        assertNull(duringProceed.get());
    }

    @Test
    @DisplayName("事务结束后上下文栈被清空")
    void contextStackIsPoppedAfterProceed() throws Throwable {
        RoutingDataSource routing = mock(RoutingDataSource.class);
        when(routing.primaryName()).thenReturn("master");

        pushedPrimary(new TransactionMasterAspect(routing, "master"));

        assertNull(DynamicDataSourceContextHolder.peek());
    }

    @SuppressWarnings("unused")
    private void transactionalSample() {
    }

    @DS("report")
    private void explicitDataSourceSample() {
    }

    private static String pushedPrimary(TransactionMasterAspect aspect) throws Throwable {
        AtomicReference<String> pushed = new AtomicReference<>();
        Object result = aspect.forceMaster(point("transactionalSample", ignored ->
                pushed.set(DynamicDataSourceContextHolder.peek())));

        assertEquals("ok", result);
        assertNull(DynamicDataSourceContextHolder.peek());
        return pushed.get();
    }

    private static ProceedingJoinPoint point(String methodName, Consumer<Object> onProceed) throws Throwable {
        ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(TransactionMasterAspectTest.class.getDeclaredMethod(methodName));
        when(point.getSignature()).thenReturn(signature);
        when(point.getTarget()).thenReturn(new TransactionMasterAspectTest());
        when(point.proceed()).thenAnswer(invocation -> {
            onProceed.accept(invocation);
            return "ok";
        });
        return point;
    }
}
