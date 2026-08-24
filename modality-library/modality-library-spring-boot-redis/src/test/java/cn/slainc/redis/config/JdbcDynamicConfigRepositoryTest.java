package cn.slainc.redis.config;

import cn.slainc.redis.RedisTestSupport;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcDynamicConfigRepositoryTest {

    @Test
    void JDBC实现具备完整仓储契约与建表初始化入口() throws Exception {
        RedisTestSupport.assumeClassPresent("org.springframework.jdbc.core.JdbcTemplate",
                "spring-boot-starter-jdbc 是 compileOnly 坐标，测试运行期不存在，已跳过；补齐 testRuntimeOnly 后会真实执行");

        Class<?> repositoryClass = Class.forName("cn.slainc.redis.config.JdbcDynamicConfigRepository");
        assertThat(DynamicConfigRepository.class.isAssignableFrom(repositoryClass)).isTrue();

        Constructor<?> beanConstructor = repositoryClass.getConstructor(
                Class.forName("org.springframework.jdbc.core.JdbcTemplate"), String.class);
        assertThat(beanConstructor).isNotNull();

        Class<?> jdbcTemplateClass = Class.forName("org.springframework.jdbc.core.JdbcTemplate");
        assertThat(repositoryClass.getConstructor(jdbcTemplateClass)).isNotNull();

        for (Method method : DynamicConfigRepository.class.getMethods()) {
            Method impl = repositoryClass.getMethod(method.getName(), method.getParameterTypes());
            assertThat(impl.getDeclaringClass()).isEqualTo(repositoryClass);
        }
        assertThat(repositoryClass.getMethod("initSchema")).isNotNull();
        assertThat(repositoryClass.getMethod("getTable")).isNotNull();
    }
}
