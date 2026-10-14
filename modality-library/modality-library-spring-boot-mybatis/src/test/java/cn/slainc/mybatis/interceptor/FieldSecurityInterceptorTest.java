package cn.slainc.mybatis.interceptor;

import cn.slainc.mybatis.crypto.FieldEncryptor;
import cn.slainc.mybatis.base.User;
import cn.slainc.mybatis.properties.MybatisProperties;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FieldSecurityInterceptorTest {

    private final FieldEncryptor encryptor = encryptor(true);

    @Test
    @DisplayName("查询参数对象中的加密字段在绑定前被加密")
    void queryEncryptsParameterObjectFields() throws Throwable {
        User probe = new User();
        probe.setSecret("top-secret");
        probe.setName("alice");
        AtomicReference<Object> bound = new AtomicReference<>();
        AtomicReference<String> secretOnBind = new AtomicReference<>();
        FieldSecurityInterceptor interceptor = new FieldSecurityInterceptor(encryptor, false);

        interceptor.intercept(queryInvocation(probe, parameter -> {
            bound.set(parameter);
            secretOnBind.set(((User) parameter).getSecret());
        }));

        assertNotEquals("top-secret", secretOnBind.get());
        assertEquals("top-secret", encryptor.decrypt(secretOnBind.get()));
        assertEquals("alice", ((User) bound.get()).getName());
    }

    @Test
    @DisplayName("查询结束调用方参数对象恢复明文")
    void queryRestoresParameterObjectAfterExecution() throws Throwable {
        User probe = new User();
        probe.setSecret("restore-me");
        FieldSecurityInterceptor interceptor = new FieldSecurityInterceptor(encryptor, false);

        interceptor.intercept(queryInvocation(probe, parameter -> {
        }));

        assertEquals("restore-me", probe.getSecret());
    }

    @Test
    @DisplayName("查询结果中的密文被透明解密")
    void queryDecryptsResultFields() throws Throwable {
        User stored = new User();
        stored.setSecret(encryptor.encrypt("from-database"));
        FieldSecurityInterceptor interceptor = new FieldSecurityInterceptor(encryptor, false);

        Object result = interceptor.intercept(queryInvocation(new User(), parameter -> {
        }, List.of(stored)));

        List<?> rows = (List<?>) result;
        assertEquals(1, rows.size());
        assertEquals("from-database", ((User) rows.get(0)).getSecret());
    }

    @Test
    @DisplayName("集合与 Map 中的加密字段同样按查询参数加密")
    void queryEncryptsNestedCollectionParameters() throws Throwable {
        User first = new User();
        first.setSecret("nested-one");
        List<User> probes = new ArrayList<>(List.of(first));
        AtomicReference<String> secretOnBind = new AtomicReference<>();
        FieldSecurityInterceptor interceptor = new FieldSecurityInterceptor(encryptor, false);

        interceptor.intercept(queryInvocation(Map.of("list", probes), parameter -> {
            List<?> list = (List<?>) ((Map<?, ?>) parameter).get("list");
            secretOnBind.set(((User) list.get(0)).getSecret());
        }));

        assertNotEquals("nested-one", secretOnBind.get());
        assertEquals("nested-one", encryptor.decrypt(secretOnBind.get()));
        assertEquals("nested-one", first.getSecret());
    }

    @Test
    @DisplayName("更新参数同样在绑定前加密且执行后恢复")
    void updateEncryptsParameterObjectFields() throws Throwable {
        User entity = new User();
        entity.setSecret("update-secret");
        AtomicReference<String> secretOnBind = new AtomicReference<>();
        FieldSecurityInterceptor interceptor = new FieldSecurityInterceptor(encryptor, false);

        interceptor.intercept(updateInvocation(entity, parameter ->
                secretOnBind.set(((User) parameter).getSecret())));

        assertNotEquals("update-secret", secretOnBind.get());
        assertEquals("update-secret", entity.getSecret());
    }

    @Test
    @DisplayName("加密关闭时不改动任何参数")
    void disabledEncryptorLeavesParametersUntouched() throws Throwable {
        User probe = new User();
        probe.setSecret("plain");
        AtomicReference<Object> bound = new AtomicReference<>();
        FieldSecurityInterceptor interceptor = new FieldSecurityInterceptor(encryptor(false), false);

        interceptor.intercept(queryInvocation(probe, bound::set));

        assertSame(probe, bound.get());
        assertEquals("plain", probe.getSecret());
    }

    @Test
    @DisplayName("简单类型参数不做处理")
    void simpleParametersAreIgnored() throws Throwable {
        AtomicReference<Object> bound = new AtomicReference<>();
        FieldSecurityInterceptor interceptor = new FieldSecurityInterceptor(encryptor, false);

        interceptor.intercept(queryInvocation(7001L, bound::set));

        assertEquals(7001L, bound.get());
    }

    @Test
    @DisplayName("空参数不产生异常")
    void nullParameterIsIgnored() throws Throwable {
        AtomicReference<Object> bound = new AtomicReference<>();
        FieldSecurityInterceptor interceptor = new FieldSecurityInterceptor(encryptor, false);

        assertNull(interceptor.intercept(queryInvocation(null, bound::set)));
        assertNull(bound.get());
    }

    private Invocation queryInvocation(Object parameter, Consumer<Object> onBind) throws Exception {
        return queryInvocation(parameter, onBind, null);
    }

    private Invocation queryInvocation(Object parameter, Consumer<Object> onBind, Object result) throws Exception {
        Executor executor = mock(Executor.class);
        when(executor.query(any(), any(), any(), any())).thenAnswer(invocation -> {
            onBind.accept(invocation.getArgument(1));
            return result;
        });
        Method method = Executor.class.getMethod("query", MappedStatement.class, Object.class,
                RowBounds.class, ResultHandler.class);
        return new Invocation(executor, method, new Object[]{null, parameter, RowBounds.DEFAULT, null});
    }

    private Invocation updateInvocation(Object parameter, Consumer<Object> onBind) throws Exception {
        Executor executor = mock(Executor.class);
        when(executor.update(any(), any())).thenAnswer(invocation -> {
            onBind.accept(invocation.getArgument(1));
            return 1;
        });
        Method method = Executor.class.getMethod("update", MappedStatement.class, Object.class);
        return new Invocation(executor, method, new Object[]{null, parameter});
    }

    private static FieldEncryptor encryptor(boolean enabled) {
        MybatisProperties properties = new MybatisProperties();
        properties.getEncrypt().setEnabled(enabled);
        properties.getEncrypt().setSecretKey("modality-interceptor-test-key");
        return new FieldEncryptor(properties);
    }
}
