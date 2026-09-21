package cn.slainc.redis.dao;

import cn.slainc.redis.RedisTestSupport;
import cn.slainc.redis.serialize.RedisSerializers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RedisSaTokenDaoTest {

    private static final String PREFIX = "modality:test:satoken:";

    private static LettuceConnectionFactory factory;

    private static StringRedisTemplate stringRedisTemplate;

    private static Class<?> daoInterface;

    private static Class<?> daoClass;

    private static Object dao;

    @BeforeAll
    static void beforeAll() {
        RedisTestSupport.assumeRedis();
        RedisTestSupport.assumeClassPresent("cn.dev33.satoken.dao.SaTokenDao",
                "sa-token-core 当前是 compileOnly 坐标，测试运行期不存在，已跳过；补齐 testRuntimeOnly 后会真实执行");
        try {
            daoInterface = Class.forName("cn.dev33.satoken.dao.SaTokenDao");
            daoClass = Class.forName("cn.slainc.redis.dao.RedisSaTokenDao");
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("加载 Sa-Token 持久层失败：" + exception.getMessage(), exception);
        }
        factory = RedisTestSupport.newConnectionFactory();
        stringRedisTemplate = RedisTestSupport.newStringRedisTemplate(factory);
        SaTokenRedisStore store = new SaTokenRedisStore(stringRedisTemplate, RedisTestSupport.newRedisTemplate(factory),
                RedisSerializers.jsonMapper(), 100);
        dao = newInstance(store);
    }

    @AfterAll
    static void afterAll() {
        if (factory != null) {
            factory.destroy();
        }
    }

    @Test
    void allSaTokenDaoMethodsImplemented() {
        List<String> missing = new ArrayList<>();
        for (Method method : daoInterface.getMethods()) {
            if (method.isDefault() || method.getDeclaringClass() == Object.class) {
                continue;
            }
            try {
                Method impl = daoClass.getMethod(method.getName(), method.getParameterTypes());
                if (impl.getDeclaringClass() != daoClass) {
                    missing.add(method.getName());
                }
            } catch (NoSuchMethodException exception) {
                missing.add(method.getName());
            }
        }
        System.out.println("[验收] SaTokenDao 抽象方法总数=" + countAbstract() + "，未实现=" + missing);
        assertThat(daoInterface.isAssignableFrom(daoClass)).isTrue();
        assertThat(missing).isEmpty();
        assertThat(countAbstract()).isEqualTo(20);
    }

    @Test
    void stringValueCrud() {
        String key = PREFIX + UUID.randomUUID().toString().replace("-", "");
        invoke("set", new Class<?>[]{String.class, String.class, long.class}, key, "值一", 60L);
        assertThat(invoke("get", new Class<?>[]{String.class}, key)).isEqualTo("值一");
        assertThat((Long) invoke("getTimeout", new Class<?>[]{String.class}, key)).isBetween(50L, 60L);

        invoke("update", new Class<?>[]{String.class, String.class}, key, "值二");
        assertThat(invoke("get", new Class<?>[]{String.class}, key)).isEqualTo("值二");
        assertThat((Long) invoke("getTimeout", new Class<?>[]{String.class}, key)).isBetween(50L, 60L);

        invoke("updateTimeout", new Class<?>[]{String.class, long.class}, key, 120L);
        assertThat(invoke("get", new Class<?>[]{String.class}, key)).isEqualTo("值二");
        assertThat((Long) invoke("getTimeout", new Class<?>[]{String.class}, key)).isBetween(110L, 120L);

        invoke("delete", new Class<?>[]{String.class}, key);
        assertThat(invoke("get", new Class<?>[]{String.class}, key)).isNull();
        assertThat((Long) invoke("getTimeout", new Class<?>[]{String.class}, key)).isEqualTo(-2L);
    }

    @Test
    void objectValueCrud() {
        String key = PREFIX + UUID.randomUUID().toString().replace("-", "");
        invoke("setObject", new Class<?>[]{String.class, Object.class, long.class}, key, "对象一", 60L);
        assertThat(invoke("getObject", new Class<?>[]{String.class}, key)).isEqualTo("对象一");
        assertThat(invoke("getObject", new Class<?>[]{String.class, Class.class}, key, String.class)).isEqualTo("对象一");
        assertThat((Long) invoke("getObjectTimeout", new Class<?>[]{String.class}, key)).isBetween(50L, 60L);

        invoke("updateObject", new Class<?>[]{String.class, Object.class}, key, "对象二");
        assertThat(invoke("getObject", new Class<?>[]{String.class}, key)).isEqualTo("对象二");

        invoke("updateObjectTimeout", new Class<?>[]{String.class, long.class}, key, 90L);
        assertThat((Long) invoke("getObjectTimeout", new Class<?>[]{String.class}, key)).isBetween(80L, 90L);

        invoke("deleteObject", new Class<?>[]{String.class}, key);
        assertThat(invoke("getObject", new Class<?>[]{String.class}, key)).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void retrievalAndLifecycleCallbacks() {
        String scope = PREFIX + "search-" + UUID.randomUUID().toString().substring(0, 6) + ":";
        stringRedisTemplate.opsForValue().set(scope + "b", "1");
        stringRedisTemplate.opsForValue().set(scope + "a", "1");
        List<String> found = (List<String>) invoke("searchData",
                new Class<?>[]{String.class, String.class, int.class, int.class, boolean.class},
                scope, "", 0, 10, true);
        assertThat(found).containsExactly(scope + "a", scope + "b");

        invoke("init", new Class<?>[]{});
        invoke("destroy", new Class<?>[]{});
        stringRedisTemplate.unlink(List.of(scope + "a", scope + "b"));
    }

    private static long countAbstract() {
        long count = 0L;
        for (Method method : daoInterface.getMethods()) {
            if (!method.isDefault() && method.getDeclaringClass() != Object.class) {
                count++;
            }
        }
        return count;
    }

    private static Object newInstance(SaTokenRedisStore store) {
        try {
            return daoClass.getConstructor(SaTokenRedisStore.class).newInstance(store);
        } catch (Exception exception) {
            throw new IllegalStateException("构造 RedisSaTokenDao 失败：" + exception.getMessage(), exception);
        }
    }

    private static Object invoke(String name, Class<?>[] types, Object... args) {
        try {
            Method method = daoClass.getMethod(name, types);
            return method.invoke(dao, args);
        } catch (Exception exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            throw new IllegalStateException("调用 " + name + " 失败：" + cause.getMessage(), cause);
        }
    }
}
