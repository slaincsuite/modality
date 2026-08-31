package cn.slainc.idempotent.support;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.idempotent.annotation.Idempotent;
import cn.slainc.idempotent.annotation.IdempotentStrategy;
import cn.slainc.idempotent.autoconfigure.IdempotentProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotentSettingsResolverTest {

    private static final String METHOD_ID = "cn.slainc.DemoService#submit";

    @Test
    void 没有动态配置服务时使用本地默认值() throws Exception {
        IdempotentSettingsResolver resolver = resolver(TestObjectProviders.empty(), new IdempotentProperties());
        IdempotentSettings settings = resolver.resolve(context(annotation("plain")));
        assertThat(settings.enabled()).isTrue();
        assertThat(settings.ttl()).isEqualTo(Duration.ofMinutes(5));
        assertThat(settings.maxRetries()).isEqualTo(3);
        assertThat(settings.retryWindow()).isEqualTo(Duration.ofMinutes(5));
        assertThat(settings.message()).isEqualTo("请勿重复提交，请稍后再试");
        assertThat(settings.strategy()).isEqualTo(IdempotentStrategy.PARAMETER);
    }

    @Test
    void 动态配置异常时回退本地默认值() throws Exception {
        DynamicConfigService broken = new MapDynamicConfigService() {
            @Override
            public Optional<String> findString(String key) {
                throw new IllegalStateException("Redis 不可用");
            }
        };
        IdempotentSettingsResolver resolver = resolver(TestObjectProviders.of(broken), new IdempotentProperties());
        IdempotentSettings settings = resolver.resolve(context(annotation("plain")));
        assertThat(settings.ttl()).isEqualTo(Duration.ofMinutes(5));
        assertThat(settings.maxRetries()).isEqualTo(3);
    }

    @Test
    void 注解属性优先于动态配置() throws Exception {
        MapDynamicConfigService dynamic = new MapDynamicConfigService()
                .put("modality.idempotent.default-ttl", "9m")
                .put("modality.idempotent.retry.max-retries", "9")
                .put("modality.idempotent.message", "动态提示");
        IdempotentSettingsResolver resolver = resolver(TestObjectProviders.of(dynamic), new IdempotentProperties());
        IdempotentSettings settings = resolver.resolve(context(annotation("annotated")));
        assertThat(settings.ttl()).isEqualTo(Duration.ofSeconds(30));
        assertThat(settings.maxRetries()).isEqualTo(2);
        assertThat(settings.retryWindow()).isEqualTo(Duration.ofSeconds(60));
        assertThat(settings.message()).isEqualTo("注解提示");
        assertThat(settings.strategy()).isEqualTo(IdempotentStrategy.HYBRID);
    }

    @Test
    void 动态配置按方法覆盖优先于全局() throws Exception {
        MapDynamicConfigService dynamic = new MapDynamicConfigService()
                .put("modality.idempotent.default-ttl", "9m")
                .put("modality.idempotent.methods." + METHOD_ID + ".default-ttl", "30s")
                .put("modality.idempotent.methods." + METHOD_ID + ".retry.max-retries", "7")
                .put("modality.idempotent.enabled", "false");
        IdempotentSettingsResolver resolver = resolver(TestObjectProviders.of(dynamic), new IdempotentProperties());
        IdempotentSettings settings = resolver.resolve(context(annotation("plain")));
        assertThat(settings.ttl()).isEqualTo(Duration.ofSeconds(30));
        assertThat(settings.maxRetries()).isEqualTo(7);
        assertThat(settings.enabled()).as("总开关可动态关闭").isFalse();
    }

    @Test
    void 忽略字段来自属性动态配置与注解的合并() throws Exception {
        IdempotentProperties properties = new IdempotentProperties();
        properties.setIgnoreFields(java.util.List.of("traceId"));
        MapDynamicConfigService dynamic = new MapDynamicConfigService()
                .put("modality.idempotent.ignore-fields", "timestamp, nonce");
        IdempotentSettingsResolver resolver = resolver(TestObjectProviders.of(dynamic), properties);
        IdempotentSettings settings = resolver.resolve(context(annotation("ignored")));
        assertThat(settings.ignoreFields()).containsExactlyInAnyOrder("traceId", "timestamp", "nonce", "extra");
    }

    @Test
    void 时间窗口支持多种写法() {
        assertThat(IdempotentSettingsResolver.parseDuration("30s")).isEqualTo(Duration.ofSeconds(30));
        assertThat(IdempotentSettingsResolver.parseDuration("5m")).isEqualTo(Duration.ofMinutes(5));
        assertThat(IdempotentSettingsResolver.parseDuration("2h")).isEqualTo(Duration.ofHours(2));
        assertThat(IdempotentSettingsResolver.parseDuration("1d")).isEqualTo(Duration.ofDays(1));
        assertThat(IdempotentSettingsResolver.parseDuration("500ms")).isEqualTo(Duration.ofMillis(500));
        assertThat(IdempotentSettingsResolver.parseDuration("PT30S")).isEqualTo(Duration.ofSeconds(30));
        assertThat(IdempotentSettingsResolver.parseDuration("45")).isEqualTo(Duration.ofSeconds(45));
        assertThat(IdempotentSettingsResolver.parseDuration("不是时间")).isNull();
        assertThat(IdempotentSettingsResolver.parseDuration(" ")).isNull();
    }

    @Test
    void 动态配置写入非法整数时回退默认值() throws Exception {
        MapDynamicConfigService dynamic = new MapDynamicConfigService()
                .put("modality.idempotent.retry.max-retries", "abc");
        IdempotentSettingsResolver resolver = resolver(TestObjectProviders.of(dynamic), new IdempotentProperties());
        assertThat(resolver.resolve(context(annotation("plain"))).maxRetries()).isEqualTo(3);
    }

    private IdempotentSettingsResolver resolver(ObjectProvider<DynamicConfigService> provider,
                                                IdempotentProperties properties) {
        return new IdempotentSettingsResolver(properties, provider);
    }

    private IdempotentContext context(Idempotent annotation) throws Exception {
        Method method = getClass().getDeclaredMethod("handle", Object.class);
        return new IdempotentContext(method, new Object[]{"载荷"}, this, annotation, METHOD_ID, "POST /demo", 1L, null,
                null, null, null);
    }

    @SuppressWarnings("unused")
    private void handle(Object payload) {
    }

    private Idempotent annotation(String name) throws Exception {
        return getClass().getDeclaredMethod(name).getAnnotation(Idempotent.class);
    }

    @Idempotent
    @SuppressWarnings("unused")
    private void plain() {
    }

    @Idempotent(ttl = 30, maxRetries = 2, retryWindow = 60, message = "注解提示", strategy = IdempotentStrategy.HYBRID)
    @SuppressWarnings("unused")
    private void annotated() {
    }

    @Idempotent(ignoreFields = {"extra"})
    @SuppressWarnings("unused")
    private void ignored() {
    }
}
