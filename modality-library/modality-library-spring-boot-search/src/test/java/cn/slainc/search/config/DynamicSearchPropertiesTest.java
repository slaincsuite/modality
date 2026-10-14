package cn.slainc.search.config;

import cn.slainc.core.config.DynamicConfig;
import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.search.support.Bm25Scorer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DynamicSearchPropertiesTest {

    @Test
    void shouldFallBackToTheBoundValuesWithoutAConfigService() {
        SearchProperties fallback = new SearchProperties();
        fallback.setMaxPageSize(120);
        fallback.setStopWords(List.of("the"));
        DynamicSearchProperties properties = new DynamicSearchProperties(fallback, provider(null));

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getMaxPageSize()).isEqualTo(120);
        assertThat(properties.getStopWords()).containsExactly("the");
        assertThat(properties.getK1()).isEqualTo(1.2D);
        assertThat(properties.getB()).isEqualTo(0.75D);
    }

    @Test
    void shouldPreferTheStoredValues() {
        FakeConfigService service = new FakeConfigService();
        service.put(DynamicSearchProperties.KEY_ENABLED, "false");
        service.put(DynamicSearchProperties.KEY_DEFAULT_PAGE_SIZE, "5");
        service.put(DynamicSearchProperties.KEY_MAX_PAGE_SIZE, "50");
        service.put(DynamicSearchProperties.KEY_MAX_CONTENT_LENGTH, "1000");
        service.put(DynamicSearchProperties.KEY_SNIPPET_LENGTH, "60");
        service.put(DynamicSearchProperties.KEY_MIN_TERM_LENGTH, "2");
        service.put(DynamicSearchProperties.KEY_STOP_WORDS, "the, a ,");
        service.put(DynamicSearchProperties.KEY_K1, "2.0");
        service.put(DynamicSearchProperties.KEY_B, "0.5");
        service.put(DynamicSearchProperties.KEY_REINDEX_BATCH_SIZE, "10");
        DynamicSearchProperties properties = new DynamicSearchProperties(new SearchProperties(), provider(service));

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getDefaultPageSize()).isEqualTo(5);
        assertThat(properties.getMaxPageSize()).isEqualTo(50);
        assertThat(properties.getMaxContentLength()).isEqualTo(1000);
        assertThat(properties.getSnippetLength()).isEqualTo(60);
        assertThat(properties.getMinTermLength()).isEqualTo(2);
        assertThat(properties.getStopWords()).containsExactly("the", "a");
        assertThat(properties.getK1()).isEqualTo(2.0D);
        assertThat(properties.getB()).isEqualTo(0.5D);
        assertThat(properties.getReindexBatchSize()).isEqualTo(10);
        assertThat(new Bm25Scorer(properties.getK1(), properties.getB()).k1()).isEqualTo(2.0D);
    }

    @Test
    void shouldIgnoreBrokenNumbersAndMissingServices() {
        FakeConfigService broken = new FakeConfigService();
        broken.put(DynamicSearchProperties.KEY_K1, "abc");
        DynamicSearchProperties properties = new DynamicSearchProperties(new SearchProperties(), provider(broken));

        assertThat(properties.getK1()).isEqualTo(1.2D);
        assertThat(new DynamicSearchProperties(new SearchProperties(), null).getMaxPageSize()).isEqualTo(100);
    }

    @Test
    void shouldTolerateAFailingConfigService() {
        DynamicConfigService failing = new FakeConfigService() {

            @Override
            public Optional<String> findString(String key) {
                throw new IllegalStateException("动态配置不可用");
            }

            @Override
            public String getString(String key, String defaultValue) {
                throw new IllegalStateException("动态配置不可用");
            }

            @Override
            public int getInt(String key, int defaultValue) {
                throw new IllegalStateException("动态配置不可用");
            }

            @Override
            public boolean getBoolean(String key, boolean defaultValue) {
                throw new IllegalStateException("动态配置不可用");
            }
        };
        SearchProperties fallback = new SearchProperties();
        fallback.setMaxPageSize(66);
        DynamicSearchProperties properties = new DynamicSearchProperties(fallback, provider(failing));

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getMaxPageSize()).isEqualTo(66);
        assertThat(properties.getStopWords()).isEmpty();
        assertThat(properties.getK1()).isEqualTo(1.2D);
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<DynamicConfigService> provider(DynamicConfigService service) {
        ObjectProvider<DynamicConfigService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(service);
        return provider;
    }

    private static class FakeConfigService implements DynamicConfigService {

        private final Map<String, String> values = new ConcurrentHashMap<>();

        void put(String key, String value) {
            values.put(key, value);
        }

        @Override
        public Optional<String> findString(String key) {
            return Optional.ofNullable(values.get(key));
        }

        @Override
        public String getString(String key, String defaultValue) {
            return values.getOrDefault(key, defaultValue);
        }

        @Override
        public int getInt(String key, int defaultValue) {
            String value = values.get(key);
            return value == null ? defaultValue : Integer.parseInt(value);
        }

        @Override
        public long getLong(String key, long defaultValue) {
            String value = values.get(key);
            return value == null ? defaultValue : Long.parseLong(value);
        }

        @Override
        public boolean getBoolean(String key, boolean defaultValue) {
            String value = values.get(key);
            return value == null ? defaultValue : Boolean.parseBoolean(value);
        }

        @Override
        public <T> T getObject(String key, Class<T> type) {
            return null;
        }

        @Override
        public <T> T getObject(String key, Class<T> type, T defaultValue) {
            return defaultValue;
        }

        @Override
        public <T> List<T> getList(String key, Class<T> elementType) {
            return List.of();
        }

        @Override
        public String getEncrypted(String key) {
            return null;
        }

        @Override
        public Map<String, String> getGroup(String group) {
            return Map.of();
        }

        @Override
        public List<DynamicConfig> listByGroup(String group) {
            return List.of();
        }

        @Override
        public void set(String key, String value) {
            values.put(key, value);
        }

        @Override
        public void set(String key, String value, String group) {
            values.put(key, value);
        }

        @Override
        public void setEncrypted(String key, String value, String group) {
            values.put(key, value);
        }

        @Override
        public void remove(String key) {
            values.remove(key);
        }

        @Override
        public void refresh(String key) {
        }

        @Override
        public void refreshAll() {
        }
    }
}
