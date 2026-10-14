package cn.slainc.search.autoconfigure;

import cn.slainc.core.config.DynamicConfig;
import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.search.analyzer.SearchAnalyzer;
import cn.slainc.search.api.SearchEngine;
import cn.slainc.search.api.SearchStats;
import cn.slainc.search.api.impl.LuceneSearchEngine;
import cn.slainc.search.config.DynamicSearchProperties;
import cn.slainc.search.config.SearchProperties;
import cn.slainc.search.index.LuceneIndexStore;
import cn.slainc.search.index.SearchIndexer;
import cn.slainc.search.source.SearchDocumentRegistry;
import cn.slainc.search.source.SearchDocumentSource;
import cn.slainc.search.source.SearchIndexRefresher;
import cn.slainc.search.spi.SearchDocumentPayload;
import cn.slainc.search.spi.SearchDocumentPort;
import cn.slainc.search.support.Bm25Scorer;
import cn.slainc.search.support.SearchQueryParser;
import cn.slainc.search.support.SnippetHighlighter;
import cn.slainc.search.tokenizer.SearchTokenizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SearchAutoConfigurationTest {

    @TempDir
    Path indexDirectory;

    private ApplicationContextRunner runner;

    @BeforeEach
    void setUp() {
        runner = baseRunner()
                .withBean(SearchDocumentPort.class, () -> mock(SearchDocumentPort.class))
                .withBean(SearchDocumentSource.class, ArticleSource::new)
                .withPropertyValues("modality.search.directory=" + indexDirectory.resolve("lucene"));
    }

    @Test
    void shouldRegisterEverySearchBean() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SearchProperties.class);
            assertThat(context).hasSingleBean(SearchTokenizer.class);
            assertThat(context).hasSingleBean(Bm25Scorer.class);
            assertThat(context).hasSingleBean(SnippetHighlighter.class);
            assertThat(context).hasSingleBean(SearchQueryParser.class);
            assertThat(context).hasSingleBean(SearchAnalyzer.class);
            assertThat(context).hasSingleBean(LuceneIndexStore.class);
            assertThat(context).hasSingleBean(SearchIndexer.class);
            assertThat(context).hasSingleBean(SearchEngine.class);
            assertThat(context).hasSingleBean(SearchDocumentRegistry.class);
            assertThat(context).hasSingleBean(SearchIndexRefresher.class);
            assertThat(context.getBean(SearchEngine.class)).isInstanceOf(LuceneSearchEngine.class);
        });
    }

    @Test
    void shouldCollectTheRegisteredDocumentSources() {
        runner.run(context -> assertThat(context.getBean(SearchDocumentRegistry.class).sourceTypes())
                .containsExactly("article"));
    }

    @Test
    void shouldBindTheSearchProperties() {
        runner.withPropertyValues("modality.search.max-page-size=42", "modality.search.stop-words=the,a",
                "modality.search.snippet-length=80").run(context -> {
            assertThat(context).hasNotFailed();
            SearchProperties properties = context.getBean(SearchProperties.class);
            assertThat(properties.getMaxPageSize()).isEqualTo(42);
            assertThat(properties.getStopWords()).containsExactly("the", "a");
            assertThat(properties.getSnippetLength()).isEqualTo(80);
            assertThat(properties.getEngine()).isEqualTo("lucene");
        });
    }

    @Test
    void shouldAssembleWithoutTheSearchDocumentPort() {
        baseRunner().withPropertyValues("modality.search.directory=" + indexDirectory.resolve("without-port")).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SearchIndexer.class);
            assertThat(context).hasSingleBean(SearchEngine.class);
            assertThat(context.getBean(SearchEngine.class).stats()).isEqualTo(SearchStats.empty());
        });
    }

    @Test
    void shouldAlwaysUseTheLuceneEngine() {
        runner.withPropertyValues("modality.search.engine=jdbc").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SearchEngine.class);
            assertThat(context.getBean(SearchEngine.class)).isInstanceOf(LuceneSearchEngine.class);
            assertThat(context).hasSingleBean(LuceneIndexStore.class);
        });
    }

    @Test
    void shouldRegisterNothingWhenTheSearchEngineIsDisabled() {
        runner.withPropertyValues("modality.search.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(SearchEngine.class);
            assertThat(context).doesNotHaveBean(SearchIndexer.class);
            assertThat(context).doesNotHaveBean(LuceneIndexStore.class);
        });
    }

    @Test
    void shouldPreferTheDynamicPropertiesWhenAConfigServiceIsPresent() {
        FakeConfigService service = new FakeConfigService();
        service.put(DynamicSearchProperties.KEY_MAX_PAGE_SIZE, "33");
        service.put(DynamicSearchProperties.KEY_K1, "2.5");
        dynamicRunner()
                .withBean(DynamicConfigService.class, () -> service)
                .withPropertyValues("modality.search.max-page-size=42",
                        "modality.search.directory=" + indexDirectory.resolve("dynamic"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    SearchProperties properties = context.getBean(SearchProperties.class);
                    assertThat(properties).isInstanceOf(DynamicSearchProperties.class);
                    assertThat(properties.getMaxPageSize()).isEqualTo(33);
                    assertThat(properties.getK1()).isEqualTo(2.5D);
                    assertThat(context.getBean(Bm25Scorer.class).k1()).isEqualTo(2.5D);
                });
    }

    @Test
    void shouldFallBackToTheBoundPropertiesWithoutAConfigService() {
        dynamicRunner()
                .withPropertyValues("modality.search.max-page-size=42",
                        "modality.search.directory=" + indexDirectory.resolve("static"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    SearchProperties properties = context.getBean(SearchProperties.class);
                    assertThat(properties).isNotInstanceOf(DynamicSearchProperties.class);
                    assertThat(properties.getMaxPageSize()).isEqualTo(42);
                });
    }

    private static ApplicationContextRunner baseRunner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SearchAutoConfiguration.class));
    }

    private static ApplicationContextRunner dynamicRunner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SearchDynamicConfiguration.class, SearchAutoConfiguration.class));
    }

    private static final class ArticleSource implements SearchDocumentSource {

        @Override
        public String sourceType() {
            return "article";
        }

        @Override
        public List<SearchDocumentPayload> loadAll() {
            return List.of();
        }

        @Override
        public List<SearchDocumentPayload> loadChangedSince(Instant since) {
            return List.of();
        }
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
