package cn.slainc.search.source;

import cn.slainc.search.spi.SearchDocumentPayload;
import cn.slainc.search.spi.SearchDocumentPort;
import cn.slainc.search.spi.SearchDocumentSnapshot;
import cn.slainc.search.spi.SearchTermRow;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SearchDocumentRegistryTest {

    @Test
    void shouldIndexTheSourcesByTypeAndIgnoreBlankOrDuplicatedOnes() {
        SearchDocumentSource article = source("article");
        SearchDocumentRegistry registry = new SearchDocumentRegistry(
                List.of(article, source("  "), source("product"), source("article")));

        assertThat(registry.sourceTypes()).containsExactly("article", "product");
        assertThat(registry.find("article")).isSameAs(article);
        assertThat(registry.find("missing")).isNull();
        assertThat(registry.sources()).hasSize(2);
    }

    @Test
    void shouldReturnEverySourceForAnEmptyFilter() {
        SearchDocumentRegistry registry = new SearchDocumentRegistry(List.of(source("article"), source("product")));

        assertThat(registry.find((Collection<String>) null)).hasSize(2);
        assertThat(registry.find(List.of())).hasSize(2);
        assertThat(registry.find(List.of("product"))).extracting(SearchDocumentSource::sourceType)
                .containsExactly("product");
    }

    @Test
    void shouldTolerateAnEmptySourceCollection() {
        SearchDocumentRegistry registry = new SearchDocumentRegistry(null);

        assertThat(registry.sourceTypes()).isEmpty();
        assertThat(registry.sources()).isEmpty();
        assertThat(registry.find(List.of("article"))).isEmpty();
    }

    @Test
    void shouldRefreshTheChangedDocumentsOnly() {
        ChangedSource changed = new ChangedSource();
        RecordingPort port = new RecordingPort();
        SearchIndexRefresher refresher =
                new SearchIndexRefresher(new SearchDocumentRegistry(List.of(changed)), provider(port));

        assertThat(refresher.refreshChanged(Instant.parse("2026-01-01T00:00:00Z"))).isEqualTo(1);

        assertThat(changed.lastSince).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(port.indexed).isEqualTo(1);

        assertThat(refresher.refreshAll()).isEqualTo(port.reindexed);
        assertThat(port.sourceTypes).isEmpty();
    }

    private static SearchDocumentSource source(String sourceType) {
        return new SearchDocumentSource() {

            @Override
            public String sourceType() {
                return sourceType;
            }

            @Override
            public List<SearchDocumentPayload> loadAll() {
                return List.of();
            }

            @Override
            public List<SearchDocumentPayload> loadChangedSince(Instant since) {
                return List.of();
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<SearchDocumentPort> provider(SearchDocumentPort port) {
        ObjectProvider<SearchDocumentPort> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(port);
        return provider;
    }

    private static final class ChangedSource implements SearchDocumentSource {

        private Instant lastSince;

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
            this.lastSince = since;
            return List.of(new SearchDocumentPayload("article", "1", "java", null, null, null, null, null));
        }
    }

    private static final class RecordingPort implements SearchDocumentPort {

        private int indexed;

        private int reindexed;

        private Collection<String> sourceTypes = new ArrayList<>();

        @Override
        public SearchDocumentSnapshot upsert(SearchDocumentPayload payload) {
            indexed++;
            return null;
        }

        @Override
        public int remove(String sourceType, String sourceId) {
            return 0;
        }

        @Override
        public int reindexAll(Collection<String> sourceTypes) {
            this.sourceTypes = sourceTypes;
            return reindexed;
        }

        @Override
        public int removeTerms(Long documentId) {
            return 0;
        }

        @Override
        public int insertTerms(Collection<SearchTermRow> terms, int batchSize) {
            return 0;
        }

        @Override
        public List<SearchTermRow> findTerms(Collection<String> terms) {
            return List.of();
        }

        @Override
        public List<SearchDocumentSnapshot> findEnabledDocuments(Collection<Long> documentIds,
                                                                 Collection<String> sourceTypes) {
            return List.of();
        }

        @Override
        public long countEnabledDocuments() {
            return 0L;
        }

        @Override
        public long countTerms() {
            return 0L;
        }

        @Override
        public LocalDateTime lastIndexedAt() {
            return null;
        }
    }
}
