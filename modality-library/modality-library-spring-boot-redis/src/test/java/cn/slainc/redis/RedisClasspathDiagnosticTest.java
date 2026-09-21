package cn.slainc.redis;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RedisClasspathDiagnosticTest {

    @Test
    void classpathDiagnosticsPrinted() {
        String[] classes = {
                "cn.dev33.satoken.dao.SaTokenDao",
                "org.springframework.jdbc.core.JdbcTemplate",
                "io.micrometer.core.instrument.MeterRegistry",
                "jakarta.validation.Validation"
        };
        for (String name : classes) {
            boolean present;
            try {
                Class.forName(name, false, getClass().getClassLoader());
                present = true;
            } catch (Throwable ignored) {
                present = false;
            }
            System.out.println("[诊断] 可选依赖 " + name + " 在测试运行期" + (present ? "存在" : "缺失"));
        }
        System.out.println("[诊断] 本地 Redis 可用性=" + RedisTestSupport.isRedisAvailable());
        assertThat(true).isTrue();
    }
}
