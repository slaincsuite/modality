package cn.slainc.boot;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;
import org.springframework.test.context.ContextConfigurationAttributes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

// 同一 JVM 里多个应用上下文会并行存活，共享同一个 Lucene 目录会抢写锁；每个上下文分配独立目录
public class TestIsolationContextCustomizerFactory implements ContextCustomizerFactory {

    @Override
    public ContextCustomizer createContextCustomizer(Class<?> testClass,
                                                      List<ContextConfigurationAttributes> configAttributes) {
        return TestIsolationCustomizer.INSTANCE;
    }

    static final class TestIsolationCustomizer implements ContextCustomizer {

        static final TestIsolationCustomizer INSTANCE = new TestIsolationCustomizer();

        private static final AtomicLong SEQ = new AtomicLong();

        @Override
        public void customizeContext(ConfigurableApplicationContext context, MergedContextConfiguration mergedConfig) {
            try {
                Path dir = Files.createTempDirectory("modality-search-" + SEQ.incrementAndGet() + "-");
                context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                        "modalityTestIsolation", Map.of(
                                "modality.search.directory", dir.toString(),
                                "modality.storage.local.root", dir.resolve("storage").toString())));
            } catch (Exception ignored) {
                // 目录创建失败时沿用默认配置
            }
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof TestIsolationCustomizer;
        }

        @Override
        public int hashCode() {
            return TestIsolationCustomizer.class.hashCode();
        }
    }
}
