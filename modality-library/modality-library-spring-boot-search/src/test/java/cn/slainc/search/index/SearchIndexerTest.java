package cn.slainc.search.index;

import cn.slainc.search.analyzer.SearchAnalyzer;
import cn.slainc.search.config.SearchProperties;
import cn.slainc.search.spi.SearchDocumentPort;
import cn.slainc.search.spi.SearchDocumentSnapshot;
import cn.slainc.search.spi.SearchTermRow;
import cn.slainc.search.tokenizer.SearchTokenizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.beans.factory.ObjectProvider;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SearchIndexerTest {

    private SearchProperties properties;

    private SearchDocumentPort port;

    private SearchIndexer indexer;

    @TempDir
    Path indexDirectory;

    @BeforeEach
    void setUp() {
        properties = new SearchProperties();
        port = mock(SearchDocumentPort.class);
        indexer = new SearchIndexer(provider(port), new SearchTokenizer(properties), properties);
    }

    @Test
    void shouldAssignTheDeclaredFieldWeights() {
        SearchDocumentSnapshot document = document(1L, "搜索", "搜索", "搜索", "搜索");

        assertThat(indexer.buildTerms(document))
                .extracting(SearchTermRow::field, SearchTermRow::term, SearchTermRow::weight)
                .containsExactlyInAnyOrder(tuple("TITLE", "搜索", 5), tuple("SUMMARY", "搜索", 2),
                        tuple("CONTENT", "搜索", 1), tuple("TAGS", "搜索", 3));
    }

    @Test
    void shouldMergeRepeatedTermsWithinTheSameField() {
        SearchDocumentSnapshot document = document(1L, null, null, "搜索 搜索 搜索", "java,java");

        assertThat(indexer.buildTerms(document))
                .extracting(SearchTermRow::field, SearchTermRow::term, SearchTermRow::weight)
                .containsExactlyInAnyOrder(tuple("CONTENT", "搜索", 3), tuple("TAGS", "java", 6));
    }

    @Test
    void shouldKeepOneRowPerFieldForTheSameTerm() {
        SearchDocumentSnapshot document = document(1L, "搜索", null, "搜索", null);

        assertThat(indexer.buildTerms(document)).hasSize(2)
                .extracting(SearchTermRow::field, SearchTermRow::weight)
                .containsExactlyInAnyOrder(tuple("TITLE", 5), tuple("CONTENT", 1));
    }

    @Test
    void shouldNotIndexMoreThanTheConfiguredContentLength() {
        properties.setMaxContentLength(2);
        SearchIndexer limited = new SearchIndexer(provider(port), new SearchTokenizer(properties), properties);
        SearchDocumentSnapshot document = document(1L, null, null, "搜索搜索搜索", null);

        assertThat(limited.buildTerms(document)).hasSize(1)
                .allSatisfy(term -> assertThat(term.term()).isEqualTo("搜索"));
    }

    @Test
    void shouldDeleteTheOldTermsBeforeWritingTheNewOnes() {
        when(port.removeTerms(9L)).thenReturn(3);
        SearchDocumentSnapshot document = document(9L, "java", null, null, null);

        assertThat(indexer.reindex(document)).isEqualTo(1);

        InOrder inOrder = inOrder(port);
        inOrder.verify(port).removeTerms(9L);
        inOrder.verify(port).insertTerms(anyCollection(), eq(200));
        assertThat(capturedTerms()).singleElement().satisfies(term -> {
            assertThat(term.documentId()).isEqualTo(9L);
            assertThat(term.term()).isEqualTo("java");
            assertThat(term.field()).isEqualTo("TITLE");
            assertThat(term.weight()).isEqualTo(5);
        });
    }

    @Test
    void shouldRebuildOnlyTheTermsOfTheLatestContent() {
        when(port.removeTerms(9L)).thenReturn(0);
        indexer.reindex(document(9L, "java", null, null, null));
        indexer.reindex(document(9L, "spring", null, null, null));

        ArgumentCaptor<Collection<SearchTermRow>> captor = captor();
        verify(port, times(2)).insertTerms(captor.capture(), eq(200));
        verify(port, times(2)).removeTerms(9L);
        assertThat(captor.getAllValues().get(0)).extracting(SearchTermRow::term).containsExactly("java");
        assertThat(captor.getAllValues().get(1)).extracting(SearchTermRow::term).containsExactly("spring");
    }

    @Test
    void shouldSkipDocumentsWithoutId() {
        SearchDocumentSnapshot document = document(null, "java", null, null, null);

        assertThat(indexer.reindex(document)).isZero();
        assertThat(indexer.reindex(null)).isZero();
        verifyNoInteractions(port);
    }

    @Test
    void shouldRemoveEveryTermOfADocument() {
        when(port.removeTerms(5L)).thenReturn(4);

        assertThat(indexer.removeByDocumentId(5L)).isEqualTo(4);
        assertThat(indexer.removeByDocumentId(null)).isZero();
    }

    @Test
    void shouldWriteTheDocumentIntoTheLuceneIndex() throws IOException {
        try (LuceneIndexStore store = newStore()) {
            SearchIndexer luceneIndexer = luceneIndexer(store);

            assertThat(luceneIndexer.reindex(document(9L, "搜索", null, "搜索", null))).isEqualTo(1);

            assertThat(store.stats().documentCount()).isEqualTo(1L);
            verifyNoInteractions(port);
        }
    }

    @Test
    void shouldRemoveTheDocumentFromTheLuceneIndex() throws IOException {
        try (LuceneIndexStore store = newStore()) {
            SearchIndexer luceneIndexer = luceneIndexer(store);
            luceneIndexer.reindex(document(9L, "搜索", null, "搜索", null));

            assertThat(luceneIndexer.removeByDocumentId(9L)).isEqualTo(1);
            assertThat(luceneIndexer.removeByDocumentId(null)).isZero();

            assertThat(store.stats().documentCount()).isZero();
            verifyNoInteractions(port);
        }
    }

    @Test
    void shouldDropDisabledDocumentsFromTheLuceneIndex() throws IOException {
        try (LuceneIndexStore store = newStore()) {
            SearchIndexer luceneIndexer = luceneIndexer(store);
            luceneIndexer.reindex(document(9L, "搜索", null, "搜索", null));
            SearchDocumentSnapshot disabled = new SearchDocumentSnapshot(9L, "article", "article-9", "搜索", null,
                    "搜索", null, null, Boolean.FALSE, null, null);

            assertThat(luceneIndexer.reindex(disabled)).isZero();

            assertThat(store.stats().documentCount()).isZero();
            verifyNoInteractions(port);
        }
    }

    private SearchIndexer luceneIndexer(LuceneIndexStore store) {
        return new SearchIndexer(provider(port), new SearchTokenizer(properties), properties, store);
    }

    private LuceneIndexStore newStore() throws IOException {
        properties.setDirectory(indexDirectory.resolve("lucene").toString());
        properties.setRefreshIntervalMs(200L);
        return new LuceneIndexStore(properties, new SearchAnalyzer(properties), "search-indexer-test");
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<SearchDocumentPort> provider(SearchDocumentPort port) {
        ObjectProvider<SearchDocumentPort> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(port);
        return provider;
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<Collection<SearchTermRow>> captor() {
        return ArgumentCaptor.forClass(Collection.class);
    }

    private List<SearchTermRow> capturedTerms() {
        ArgumentCaptor<Collection<SearchTermRow>> captor = captor();
        verify(port).insertTerms(captor.capture(), eq(200));
        return List.copyOf(captor.getValue());
    }

    private static SearchDocumentSnapshot document(Long id) {
        return document(id, null, null, null, null);
    }

    private static SearchDocumentSnapshot document(Long id, String title, String summary, String content,
                                                   String tags) {
        return new SearchDocumentSnapshot(id, "article", "article-" + id, title, summary, content, tags, null,
                Boolean.TRUE, null, null);
    }
}
