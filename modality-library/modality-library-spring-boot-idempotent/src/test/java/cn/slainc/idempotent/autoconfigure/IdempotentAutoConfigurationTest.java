package cn.slainc.idempotent.autoconfigure;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.idempotent.aspect.IdempotentAspect;
import cn.slainc.idempotent.metrics.IdempotentMetrics;
import cn.slainc.idempotent.metrics.LocalIdempotentMetrics;
import cn.slainc.idempotent.metrics.MicrometerIdempotentMetrics;
import cn.slainc.idempotent.spi.IdempotentStrategyRegistry;
import cn.slainc.idempotent.store.DatabaseIdempotentStore;
import cn.slainc.idempotent.store.IdempotentStore;
import cn.slainc.idempotent.store.LocalIdempotentStore;
import cn.slainc.idempotent.store.RedisIdempotentStore;
import cn.slainc.idempotent.support.IdempotentExecutor;
import cn.slainc.idempotent.token.IdempotentTokenService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotentAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IdempotentAutoConfiguration.class,
                    IdempotentMetricsAutoConfiguration.class,
                    IdempotentRedisStoreAutoConfiguration.class,
                    IdempotentDatabaseStoreAutoConfiguration.class,
                    IdempotentLocalStoreAutoConfiguration.class,
                    IdempotentMissingStoreAutoConfiguration.class));

    @Test
    void fallsBackToLocalStoreWithoutRedisClient() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(IdempotentStore.class)).isInstanceOf(LocalIdempotentStore.class);
            assertThat(context).hasSingleBean(IdempotentAspect.class);
            assertThat(context).hasSingleBean(IdempotentExecutor.class);
            assertThat(context).hasSingleBean(LocalIdempotentMetrics.class);
            assertThat(context).hasSingleBean(IdempotentStrategyRegistry.class);
        });
    }

    @Test
    void redisStoreChosenWhenConnectionFactoryPresent() {
        runner.withBean(RedisConnectionFactory.class, () -> Mockito.mock(RedisConnectionFactory.class))
                .withBean(StringRedisTemplate.class,
                        () -> new StringRedisTemplate(Mockito.mock(RedisConnectionFactory.class)))
                .run(context -> assertThat(context.getBean(IdempotentStore.class))
                        .isInstanceOf(RedisIdempotentStore.class));
    }

    @Test
    void localStoreWinsWhenExplicitlyConfigured() {
        runner.withPropertyValues("modality.idempotent.store.type=local")
                .withBean(RedisConnectionFactory.class, () -> Mockito.mock(RedisConnectionFactory.class))
                .withBean(StringRedisTemplate.class,
                        () -> new StringRedisTemplate(Mockito.mock(RedisConnectionFactory.class)))
                .run(context -> assertThat(context.getBean(IdempotentStore.class))
                        .isInstanceOf(LocalIdempotentStore.class));
    }

    @Test
    void failFastWithChineseReasonWhenJdbcTemplateMissing() {
        runner.withPropertyValues("modality.idempotent.store.type=database").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("幂等存储未就绪");
        });
    }

    @Test
    void jdbcStoreChosenWhenConfiguredAndTemplatePresent() {
        runner.withPropertyValues("modality.idempotent.store.type=database")
                .withBean(JdbcTemplate.class, () -> new JdbcTemplate(new DriverManagerDataSource(
                        "jdbc:h2:mem:idempotent_config_test;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "")))
                .run(context -> assertThat(context.getBean(IdempotentStore.class))
                        .isInstanceOf(DatabaseIdempotentStore.class));
    }

    @Test
    void userStoreOverridesDefault() {
        LocalIdempotentStore custom = new LocalIdempotentStore();
        runner.withBean(IdempotentStore.class, () -> custom)
                .run(context -> assertThat(context.getBean(IdempotentStore.class)).isSameAs(custom));
    }

    @Test
    void micrometerMetricsRegisteredWhenRegistryPresent() {
        runner.withBean(MeterRegistry.class, SimpleMeterRegistry::new).run(context -> {
            assertThat(context).hasSingleBean(MicrometerIdempotentMetrics.class);
            assertThat(context.getBean(IdempotentMetrics.class)).isInstanceOf(MicrometerIdempotentMetrics.class);
            assertThat(context.getBean(MicrometerIdempotentMetrics.class).snapshot().requests()).isZero();
        });
    }

    @Test
    void aspectNotRegisteredWhenDisabled() {
        runner.withPropertyValues("modality.idempotent.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(IdempotentAspect.class);
            assertThat(context).doesNotHaveBean(IdempotentExecutor.class);
        });
    }

    @Test
    void wiringWorksWithoutConfigService() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(DynamicConfigService.class);
            assertThat(context).hasSingleBean(IdempotentStrategyRegistry.class);
        });
    }

    @Test
    void tokenServiceRegisteredAsBeanAndDisableable() {
        WebApplicationContextRunner webRunner = new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(IdempotentAutoConfiguration.class,
                        IdempotentLocalStoreAutoConfiguration.class, IdempotentTokenAutoConfiguration.class))
                .withPropertyValues("modality.idempotent.store.type=local");
        webRunner.run(context -> assertThat(context).hasSingleBean(IdempotentTokenService.class));
        webRunner.withPropertyValues("modality.idempotent.token.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(IdempotentTokenService.class);
        });
    }

    @Test
    void noMvcControllersRegisteredInLibrary() {
        runner.run(context -> assertThat(context.getBeansWithAnnotation(
                org.springframework.web.bind.annotation.RestController.class)).isEmpty());
    }

    @Test
    void autoConfigRegisteredInImportsFile() throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(
                "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")) {
            assertThat(input).isNotNull();
            String content = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(content).contains("cn.slainc.idempotent.autoconfigure.IdempotentAutoConfiguration")
                    .contains("cn.slainc.idempotent.autoconfigure.IdempotentRedisStoreAutoConfiguration")
                    .contains("cn.slainc.idempotent.autoconfigure.IdempotentDatabaseStoreAutoConfiguration")
                    .contains("cn.slainc.idempotent.autoconfigure.IdempotentLocalStoreAutoConfiguration")
                    .contains("cn.slainc.idempotent.autoconfigure.IdempotentMetricsAutoConfiguration")
                    .contains("cn.slainc.idempotent.autoconfigure.IdempotentTokenAutoConfiguration");
        }
    }

}
