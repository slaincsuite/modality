package cn.slainc.search.api.impl;

import cn.slainc.core.result.PageResult;
import cn.slainc.search.analyzer.SearchAnalyzer;
import cn.slainc.search.api.SearchHit;
import cn.slainc.search.api.SearchQuery;
import cn.slainc.search.api.SearchStats;
import cn.slainc.search.config.SearchProperties;
import cn.slainc.search.index.LuceneIndexStore;
import cn.slainc.search.index.SearchIndexer;
import cn.slainc.search.spi.SearchDocumentSnapshot;
import cn.slainc.search.support.SearchQueryParser;
import cn.slainc.search.tokenizer.SearchTokenizer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class LuceneSearchEngineTest {

    private static final long TENANT_A = 11L;

    private static final long TENANT_B = 22L;

    @TempDir
    Path indexDirectory;

    private SearchProperties properties;

    private LuceneIndexStore indexStore;

    private SearchIndexer indexer;

    private LuceneSearchEngine engine;

    private final AtomicReference<Long> tenant = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        properties = new SearchProperties();
        properties.setDirectory(indexDirectory.resolve("lucene").toString());
        properties.setRefreshIntervalMs(200L);
        indexStore = newStore(properties);
        indexer = new SearchIndexer(null, new SearchTokenizer(properties), properties, indexStore);
        engine = new LuceneSearchEngine(indexStore, new SearchQueryParser(new SearchTokenizer(properties)), properties,
                tenant::get);
    }

    @AfterEach
    void tearDown() {
        indexStore.close();
    }

    @Test
    void shouldHitDocumentsByAdjacentCjkBigrams() {
        index(1L, "标题", "中国人民站起来了");
        index(2L, "标题", "人民中国");

        assertThat(search("中国")).containsExactlyInAnyOrder(1L, 2L);
        assertThat(search("国人")).containsExactly(1L);
        assertThat(search("民中")).containsExactly(2L);
    }

    @Test
    void shouldHitDocumentsByEnglishTermsIgnoringCase() {
        index(1L, "标题", "Spring Boot 与 Lucene");

        assertThat(search("spring")).containsExactly(1L);
        assertThat(search("SPRING")).containsExactly(1L);
        assertThat(search("lucene")).containsExactly(1L);
        assertThat(search("hibernate")).isEmpty();
    }

    @Test
    void shouldRequireEveryTermForAndSemantics() {
        index(1L, "标题", "java spring");
        index(2L, "标题", "java");

        assertThat(search("java spring")).containsExactly(1L);
        SearchHit hit = engine.search(SearchQuery.of("java spring", 1, 10)).records().get(0);
        assertThat(hit.matchedTerms()).containsExactlyInAnyOrder("java", "spring");
    }

    @Test
    void shouldRankTitleHitsBeforeContentHits() {
        index(1L, "lucene", "无关内容");
        index(2L, "无关标题", "lucene 内容");

        PageResult<SearchHit> result = engine.search(SearchQuery.of("lucene", 1, 10));

        assertThat(ids(result)).containsExactly(1L, 2L);
        assertThat(result.records().get(0).score()).isGreaterThan(result.records().get(1).score());
    }

    @Test
    void shouldMatchOnlyTheExactPhrase() {
        index(1L, "标题", "中国人民站起来了");
        index(2L, "标题", "人民中国");
        index(3L, "标题", "中国 人民 分开");

        assertThat(search("\"中国人民\"")).containsExactly(1L);
        assertThat(search("\"人民中国\"")).containsExactly(2L);
        assertThat(search("\"中国 人民\"")).containsExactly(3L);
    }

    @Test
    void shouldExcludeDocumentsMatchingAMinusTerm() {
        index(1L, "标题", "java 服务端");
        index(2L, "标题", "java spring boot");

        assertThat(search("java -spring")).containsExactly(1L);
    }

    @Test
    void shouldExcludeDocumentsMatchingAMinusPhrase() {
        index(1L, "标题", "java 服务端");
        index(2L, "标题", "java spring boot");

        assertThat(search("java -\"spring boot\"")).containsExactly(1L);
    }

    @Test
    void shouldFilterStopWordsFromTheIndexAndTheQuery() throws IOException {
        SearchProperties stopped = new SearchProperties();
        stopped.setDirectory(indexDirectory.resolve("stop-words").toString());
        stopped.setRefreshIntervalMs(200L);
        stopped.setStopWords(List.of("the", "搜索"));
        SearchAnalyzer analyzer = new SearchAnalyzer(stopped);
        try (LuceneIndexStore store = new LuceneIndexStore(stopped, analyzer, "stop-words")) {
            SearchTokenizer tokenizer = new SearchTokenizer(stopped);
            SearchIndexer stoppedIndexer = new SearchIndexer(null, tokenizer, stopped, store);
            stoppedIndexer.reindex(snapshot(1L, "标题", "the spring"));
            stoppedIndexer.reindex(snapshot(2L, "标题", "搜索搜索引擎"));
            LuceneSearchEngine stoppedEngine = new LuceneSearchEngine(store, new SearchQueryParser(tokenizer), stopped,
                    () -> null);

            assertThat(ids(stoppedEngine.search(SearchQuery.of("the", 1, 10)))).isEmpty();
            assertThat(ids(stoppedEngine.search(SearchQuery.of("spring", 1, 10)))).containsExactly(1L);
            assertThat(ids(stoppedEngine.search(SearchQuery.of("搜索", 1, 10)))).isEmpty();
            assertThat(ids(stoppedEngine.search(SearchQuery.of("引擎", 1, 10)))).containsExactly(2L);
        }
    }

    @Test
    void shouldEscapeHtmlInSnippets() {
        index(1L, "标题", "<script>alert(1)</script> java 教程");

        SearchHit hit = engine.search(SearchQuery.of("java", 1, 10)).records().get(0);

        assertThat(hit.snippet()).doesNotContain("<script>").contains("&lt;script&gt;").contains("<em>java</em>");
        assertThat(hit.title()).isEqualTo("标题");
    }

    @Test
    void shouldNotHighlightWhenTheQueryDisablesIt() {
        index(1L, "标题", "<script>alert(1)</script> java 教程");

        SearchHit hit = engine.search(new SearchQuery("java", Set.of(), 1, 10, false)).records().get(0);

        assertThat(hit.snippet()).doesNotContain("<em>").doesNotContain("<script>").contains("&lt;script&gt;");
    }

    @Test
    void shouldIsolateTenantsWhenTheTenantContextIsPresent() {
        index(1L, TENANT_A, "标题", "中国人民");
        index(2L, TENANT_B, "标题", "中国人民");

        tenant.set(TENANT_A);
        assertThat(search("中国")).containsExactly(1L);

        tenant.set(TENANT_B);
        assertThat(search("中国")).containsExactly(2L);
    }

    @Test
    void shouldReturnEveryTenantWithoutATenantContext() {
        LuceneSearchEngine platformEngine = new LuceneSearchEngine(indexStore,
                new SearchQueryParser(new SearchTokenizer(properties)), properties);
        index(1L, TENANT_A, "标题", "中国人民");
        index(2L, TENANT_B, "标题", "中国人民");

        tenant.set(null);
        assertThat(search("中国")).containsExactlyInAnyOrder(1L, 2L);
        assertThat(ids(platformEngine.search(SearchQuery.of("中国", 1, 10)))).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void shouldFilterHitsBySourceTypes() {
        indexer.reindex(new SearchDocumentSnapshot(1L, "article", "article-1", "标题", null, "中国人民", null, null,
                Boolean.TRUE, LocalDateTime.now(), null));
        indexer.reindex(new SearchDocumentSnapshot(2L, "file", "file-2", "标题", null, "中国人民", null, null,
                Boolean.TRUE, LocalDateTime.now(), null));

        assertThat(ids(engine.search(new SearchQuery("中国", Set.of("file"), 1, 10, true)))).containsExactly(2L);
        assertThat(ids(engine.search(new SearchQuery("中国", Set.of(), 1, 10, true)))).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void shouldPaginateTheRankedHits() {
        index(1L, "标题", "java java java");
        index(2L, "标题", "java java");
        index(3L, "标题", "java");

        PageResult<SearchHit> secondPage = engine.search(SearchQuery.of("java", 2, 1));

        assertThat(secondPage.total()).isEqualTo(3L);
        assertThat(secondPage.current()).isEqualTo(2L);
        assertThat(secondPage.size()).isEqualTo(1L);
        assertThat(ids(secondPage)).containsExactly(2L);

        PageResult<SearchHit> outOfRange = engine.search(SearchQuery.of("java", 9, 1));
        assertThat(outOfRange.records()).isEmpty();
        assertThat(outOfRange.total()).isEqualTo(3L);
    }

    @Test
    void shouldClampThePageSizeToTheConfiguredMaximum() {
        properties.setMaxPageSize(2);
        index(1L, "标题", "java");

        assertThat(engine.search(SearchQuery.of("java", 1, 50)).size()).isEqualTo(2L);
    }

    @Test
    void shouldApplyTheConfiguredBm25Parameters() throws IOException {
        assertThat(scoreWithK1(0.2D)).isNotEqualTo(scoreWithK1(5.0D));
    }

    @Test
    void shouldReportTheIndexStats() {
        index(1L, "中国人民", "索引统计");
        index(2L, "标题", "spring boot");

        SearchStats stats = engine.stats();

        assertThat(stats.documentCount()).isEqualTo(2L);
        assertThat(stats.termCount()).isPositive();
        assertThat(stats.lastIndexedAt()).isNotNull();
    }

    @Test
    void shouldRemoveDocumentsFromTheIndex() {
        index(1L, "标题", "中国人民");
        index(2L, "标题", "搜索引擎");

        assertThat(indexer.removeByDocumentId(1L)).isEqualTo(1);

        assertThat(search("中国")).isEmpty();
        assertThat(search("搜索")).containsExactly(2L);
        assertThat(engine.stats().documentCount()).isEqualTo(1L);
    }

    @Test
    void shouldDropDisabledDocumentsFromTheIndex() {
        index(1L, "标题", "中国人民");
        assertThat(search("中国")).containsExactly(1L);

        SearchDocumentSnapshot disabled = new SearchDocumentSnapshot(1L, "article", "article-1", "标题", null,
                "中国人民", null, null, Boolean.FALSE, LocalDateTime.now(), null);

        assertThat(indexer.reindex(disabled)).isZero();
        assertThat(search("中国")).isEmpty();
        assertThat(engine.stats().documentCount()).isZero();
    }

    @Test
    void shouldReturnAnEmptyPageWithoutPositiveCriteria() {
        index(1L, "标题", "中国人民");

        assertThat(engine.search(SearchQuery.of("   ", 1, 10)).records()).isEmpty();
        assertThat(engine.search(SearchQuery.of("-中国", 1, 10)).records()).isEmpty();
        assertThat(engine.search(SearchQuery.of("-中国", 1, 10)).total()).isZero();
    }

    @Test
    void shouldDegradeToEmptyResultsWithoutAnIndex() {
        LuceneSearchEngine degraded = new LuceneSearchEngine(null, null, null);

        assertThat(degraded.search(SearchQuery.of("java", 1, 10)).records()).isEmpty();
        assertThat(degraded.stats()).isEqualTo(SearchStats.empty());
    }

    private double scoreWithK1(double k1) throws IOException {
        SearchProperties custom = new SearchProperties();
        custom.setDirectory(indexDirectory.resolve("bm25-" + k1).toString());
        custom.setRefreshIntervalMs(200L);
        custom.setK1(k1);
        SearchTokenizer tokenizer = new SearchTokenizer(custom);
        try (LuceneIndexStore store = new LuceneIndexStore(custom, new SearchAnalyzer(custom), "bm25")) {
            SearchIndexer storeIndexer = new SearchIndexer(null, tokenizer, custom, store);
            storeIndexer.reindex(snapshot(1L, "标题", "java java java java java java java java java java"));
            LuceneSearchEngine storeEngine = new LuceneSearchEngine(store, new SearchQueryParser(tokenizer), custom,
                    () -> null);
            return storeEngine.search(SearchQuery.of("java", 1, 10)).records().get(0).score();
        }
    }

    private LuceneIndexStore newStore(SearchProperties source) throws IOException {
        return new LuceneIndexStore(source, new SearchAnalyzer(source), "lucene-engine-test");
    }

    private void index(long id, String title, String content) {
        index(id, null, title, content);
    }

    private void index(long id, Long tenantId, String title, String content) {
        indexer.reindex(snapshot(id, title, content, tenantId));
    }

    private List<Long> search(String keyword) {
        return ids(engine.search(SearchQuery.of(keyword, 1, 20)));
    }

    private static List<Long> ids(PageResult<SearchHit> result) {
        return result.records().stream().map(SearchHit::documentId).toList();
    }

    private static SearchDocumentSnapshot snapshot(Long id, String title, String content) {
        return snapshot(id, title, content, null);
    }

    private static SearchDocumentSnapshot snapshot(Long id, String title, String content, Long tenantId) {
        return new SearchDocumentSnapshot(id, "article", "article-" + id, title, null, content, null, null,
                Boolean.TRUE, LocalDateTime.now(), tenantId);
    }
}
