package cn.slainc.captcha.autoconfigure;

import cn.slainc.captcha.CaptchaMetrics;
import cn.slainc.captcha.CaptchaProperties;
import cn.slainc.captcha.CaptchaService;
import cn.slainc.captcha.CaptchaType;
import cn.slainc.captcha.CaptchaTypeResolver;
import cn.slainc.captcha.store.CaptchaStore;
import cn.slainc.captcha.store.MemoryCaptchaStore;
import cn.slainc.captcha.store.RedisCaptchaStore;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CaptchaAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    CaptchaStoreAutoConfiguration.class,
                    CaptchaAutoConfiguration.class,
                    CaptchaMicrometerAutoConfiguration.class));

    @Test
    void redisStoreIsSelectedByDefault() {
        runner.withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(CaptchaStore.class);
                    assertThat(context.getBean(CaptchaStore.class)).isInstanceOf(RedisCaptchaStore.class);
                    assertThat(context).hasSingleBean(CaptchaService.class);
                    assertThat(context).hasSingleBean(CaptchaTypeResolver.class);
                    assertThat(context).hasSingleBean(CaptchaMetrics.class);
                });
    }

    @Test
    void memoryStoreIsSelectedWhenConfigured() {
        runner.withPropertyValues("modality.captcha.store=memory")
                .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(CaptchaStore.class);
                    assertThat(context.getBean(CaptchaStore.class)).isInstanceOf(MemoryCaptchaStore.class);
                });
    }

    @Test
    void memoryStoreFallsBackWithoutRedisTemplate() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(CaptchaStore.class);
            assertThat(context.getBean(CaptchaStore.class)).isInstanceOf(MemoryCaptchaStore.class);
        });
    }

    @Test
    void customStorePreventsBuiltInStores() {
        CaptchaStore custom = new MemoryCaptchaStore();
        runner.withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .withBean("customStore", CaptchaStore.class, () -> custom)
                .run(context -> {
                    assertThat(context).hasSingleBean(CaptchaStore.class);
                    assertThat(context.getBean(CaptchaStore.class)).isSameAs(custom);
                });
    }

    @Test
    void captchaDisabledRemovesServiceButKeepsStore() {
        runner.withPropertyValues("modality.captcha.enabled=false")
                .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(CaptchaStore.class);
                    assertThat(context).doesNotHaveBean(CaptchaService.class);
                    assertThat(context).doesNotHaveBean(CaptchaTypeResolver.class);
                    assertThat(context).doesNotHaveBean(CaptchaMetrics.class);
                });
    }

    @Test
    void metricsBinderRegistersGaugesWithRegistry() {
        runner.withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .withBean("meterRegistry", MeterRegistry.class, SimpleMeterRegistry::new)
                .run(context -> {
                    assertThat(context).hasSingleBean(CaptchaMicrometerBinder.class);
                    MeterRegistry registry = context.getBean(MeterRegistry.class);
                    assertThat(registry.get("modality.captcha.generated").gauge()).isNotNull();
                    assertThat(registry.get("modality.captcha.verify.success").gauge()).isNotNull();
                    assertThat(registry.get("modality.captcha.verify.failure").gauge()).isNotNull();
                    assertThat(registry.get("modality.captcha.locked").gauge()).isNotNull();
                });
    }

    @Test
    void metricsBinderAbsentWithoutRegistry() {
        runner.withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .run(context -> assertThat(context).doesNotHaveBean(CaptchaMicrometerBinder.class));
    }

    @Test
    void metricsBinderRemovableByProperty() {
        runner.withPropertyValues("modality.captcha.metrics.enabled=false")
                .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .withBean("meterRegistry", MeterRegistry.class, SimpleMeterRegistry::new)
                .run(context -> assertThat(context).doesNotHaveBean(CaptchaMicrometerBinder.class));
    }

    @Test
    void serviceResolvesDefaultTypeFromProperties() {
        runner.withPropertyValues("modality.captcha.type=ARITHMETIC")
                .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .run(context -> {
                    CaptchaTypeResolver resolver = context.getBean(CaptchaTypeResolver.class);
                    assertThat(resolver.resolve()).isEqualTo(CaptchaType.ARITHMETIC);
                });
    }

    @Test
    void propertiesBindDefaults() {
        runner.withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .run(context -> {
                    CaptchaProperties properties = context.getBean(CaptchaProperties.class);
                    assertThat(properties.getLength()).isEqualTo(4);
                    assertThat(properties.getTtlSeconds()).isEqualTo(300);
                    assertThat(properties.getFailThreshold()).isEqualTo(3);
                    assertThat(properties.getIpLimitPerMinute()).isEqualTo(30);
                    assertThat(properties.getGlobalLimitPerMinute()).isEqualTo(1000);
                    assertThat(properties.getImage().getWidth()).isEqualTo(130);
                    assertThat(properties.getImage().getHeight()).isEqualTo(48);
                    assertThat(properties.getSlide().getTolerancePx()).isEqualTo(5);
                    assertThat(properties.getClick().getTolerancePx()).isEqualTo(15);
                });
    }

    @Test
    void importsFileRegistersAllThreeAutoConfigurations() throws Exception {
        try (var in = getClass().getResourceAsStream(
                "/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")) {
            String content = new String(in.readAllBytes());
            assertThat(content).contains("cn.slainc.captcha.autoconfigure.CaptchaStoreAutoConfiguration");
            assertThat(content).contains("cn.slainc.captcha.autoconfigure.CaptchaAutoConfiguration");
            assertThat(content).contains("cn.slainc.captcha.autoconfigure.CaptchaMicrometerAutoConfiguration");
        }
    }

    @Test
    void webApplicationContextBootsCaptchaService() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CaptchaStoreAutoConfiguration.class))
                .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .run(context -> assertThat(context).hasSingleBean(CaptchaStore.class));
    }
}
