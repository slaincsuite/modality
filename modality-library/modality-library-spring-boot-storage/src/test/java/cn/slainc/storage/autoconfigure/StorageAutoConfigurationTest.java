package cn.slainc.storage.autoconfigure;

import cn.slainc.storage.StorageTemplate;
import cn.slainc.storage.local.LocalFileStorage;
import cn.slainc.storage.metrics.StorageMetricsRecorder;
import cn.slainc.storage.routing.RoutingFileStorage;
import cn.slainc.storage.routing.StorageBackendRegistry;
import cn.slainc.storage.url.SignedUrlService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class StorageAutoConfigurationTest {

    @TempDir
    Path root;

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(StorageAutoConfiguration.class,
                        StorageMetricsAutoConfiguration.class))
                .withPropertyValues("modality.storage.local.root=" + root);
    }

    @Test
    @DisplayName("默认装配本地后端、路由存储、模板与签名 URL 服务")
    void wiresDefaultBeans() {
        runner().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(LocalFileStorage.class);
            assertThat(context).hasSingleBean(StorageBackendRegistry.class);
            assertThat(context).hasSingleBean(RoutingFileStorage.class);
            assertThat(context).hasSingleBean(StorageTemplate.class);
            assertThat(context).hasSingleBean(SignedUrlService.class);
            RoutingFileStorage storage = context.getBean(RoutingFileStorage.class);
            assertThat(storage.activeBackend()).isEqualTo("local");
            assertThat(storage.registry().names()).contains("local");
        });
    }

    @Test
    @DisplayName("存在 MeterRegistry 时装配 Micrometer 指标，否则退避")
    void metricsBeanIsConditional() {
        runner().run(context -> assertThat(context).doesNotHaveBean(StorageMetricsRecorder.class));
        runner().withBean(MeterRegistry.class, SimpleMeterRegistry::new).run(context -> {
            assertThat(context).hasSingleBean(StorageMetricsRecorder.class);
        });
    }

    @Test
    @DisplayName("modality.storage.enabled=false 时整体退避")
    void disabledByProperty() {
        runner().withPropertyValues("modality.storage.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(StorageTemplate.class);
            assertThat(context).doesNotHaveBean(LocalFileStorage.class);
        });
    }

    @Test
    @DisplayName("使用方自定义 FileStorage 时覆盖默认本地实现")
    void customBackendOverridesDefault() {
        runner().withBean("customFileStorage", LocalFileStorage.class,
                () -> new LocalFileStorage("custom", root.resolve("custom"), null)).run(context -> {
            RoutingFileStorage storage = context.getBean(RoutingFileStorage.class);
            assertThat(storage.registry().names()).contains("custom");
        });
    }

    @Test
    @DisplayName("加密与配额开关按配置启用")
    void optionalWrappers() {
        runner().withPropertyValues("modality.storage.encryption.enabled=true",
                "modality.storage.encryption.key=0123456789abcdef0123456789abcdef",
                "modality.storage.quota.enabled=true",
                "modality.storage.quota.tenant-limit=1024").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(StorageTemplate.class)).isNotNull();
        });
    }
}
