package cn.slainc.modules.infra.service.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.PageResult;
import cn.slainc.modules.infra.entity.SearchDocumentRecord;
import cn.slainc.modules.infra.entity.SearchIndexTermRecord;
import cn.slainc.modules.infra.mapper.SearchDocumentMapper;
import cn.slainc.modules.infra.mapper.SearchIndexTermMapper;
import cn.slainc.search.config.SearchProperties;
import cn.slainc.search.index.SearchIndexer;
import cn.slainc.search.source.SearchDocumentRegistry;
import cn.slainc.search.source.SearchDocumentSource;
import cn.slainc.search.spi.SearchDocumentPayload;
import cn.slainc.search.spi.SearchDocumentPort;
import cn.slainc.search.spi.SearchDocumentSnapshot;
import cn.slainc.search.spi.SearchTermRow;
import cn.slainc.search.tokenizer.SearchTokenizer;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

class SearchDocumentServiceTest {

    private SearchProperties properties;

    private SearchDocumentMapper documentMapper;

    private SearchIndexTermMapper termMapper;

    private SearchDocumentService service;

    @BeforeAll
    static void registerTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SearchDocumentRecord.class);
        TableInfoHelper.initTableInfo(assistant, SearchIndexTermRecord.class);
    }

    @BeforeEach
    void setUp() {
        properties = new SearchProperties();
        properties.setReindexBatchSize(50);
        documentMapper = mock(SearchDocumentMapper.class);
        termMapper = mock(SearchIndexTermMapper.class);
        AtomicReference<SearchDocumentPort> port = new AtomicReference<>();
        SearchIndexer indexer = new SearchIndexer(provider(port), new SearchTokenizer(properties), properties);
        service = new SearchDocumentService(documentMapper, termMapper, indexer,
                new SearchDocumentRegistry(List.of(new FixedSource())), properties);
        port.set(service);
    }

    @Test
    void shouldInsertANewDocumentAndRebuildItsTerms() {
        stubInsertAssigningId();
        when(documentMapper.selectOne(any())).thenReturn(null);

        SearchDocumentService.DocumentView saved = service.index(payload("article", "1", "java"));

        assertThat(saved.id()).isEqualTo(100L);
        assertThat(saved.indexedAt()).isNotNull();
        assertThat(saved.enabled()).isTrue();
        assertThat(saved.sourceType()).isEqualTo("article");
        ArgumentCaptor<SearchDocumentRecord> captor = ArgumentCaptor.forClass(SearchDocumentRecord.class);
        verify(documentMapper).insert(captor.capture());
        assertThat(captor.getValue().getSourceType()).isEqualTo("article");
        verify(termMapper).insert(anyCollection(), eq(50));
    }

    @Test
    void shouldReturnTheIndexedDocumentAsAPortSnapshot() {
        stubInsertAssigningId();
        when(documentMapper.selectOne(any())).thenReturn(null);

        SearchDocumentSnapshot snapshot = service.upsert(payload("article", "1", "java"));

        assertThat(snapshot.id()).isEqualTo(100L);
        assertThat(snapshot.sourceType()).isEqualTo("article");
        assertThat(snapshot.sourceId()).isEqualTo("1");
        assertThat(snapshot.title()).isEqualTo("java");
        assertThat(snapshot.content()).isEqualTo("java");
        assertThat(snapshot.enabled()).isTrue();
        assertThat(snapshot.indexedAt()).isNotNull();
        verify(termMapper).insert(anyCollection(), eq(50));
    }

    @Test
    void shouldUpdateAnExistingDocumentKeepingItsIdentity() {
        SearchDocumentRecord existing = new SearchDocumentRecord();
        existing.setId(7L);
        existing.setCreateTime(1000L);
        stubInsertAssigningId();
        when(documentMapper.selectOne(any())).thenReturn(existing, reloadedOf(7L));

        SearchDocumentService.DocumentView saved = service.index(payload("article", "1", "java"));

        assertThat(saved.id()).isEqualTo(7L);
        verify(documentMapper, never()).insert(any(SearchDocumentRecord.class));
        ArgumentCaptor<SearchDocumentRecord> captor = ArgumentCaptor.forClass(SearchDocumentRecord.class);
        verify(documentMapper).updateById(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(7L);
        assertThat(captor.getValue().getCreateTime()).isEqualTo(1000L);
        verify(termMapper).insert(anyCollection(), eq(50));
    }

    @Test
    void shouldRejectDocumentsWithoutASource() {
        assertThatThrownBy(() -> service.index(null)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.index(new SearchDocumentPayload("", "1", "t", null, "c", null, null, null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("来源类型");
        assertThatThrownBy(() -> service.index(new SearchDocumentPayload("article", " ", "t", null, "c", null,
                null, null))).isInstanceOf(BizException.class)
                .hasMessageContaining("来源主键");
    }

    @Test
    void shouldRemoveTheDocumentTogetherWithItsTerms() {
        SearchDocumentRecord existing = new SearchDocumentRecord();
        existing.setId(7L);
        when(documentMapper.selectOne(any())).thenReturn(existing);

        assertThat(service.remove("article", "1")).isEqualTo(1);

        verify(termMapper).delete(any());
        verify(documentMapper).deleteById(7L);
    }

    @Test
    void shouldReturnZeroWhenTheDocumentDoesNotExist() {
        when(documentMapper.selectOne(any())).thenReturn(null);

        assertThat(service.remove("article", "404")).isZero();
        assertThat(service.remove(null, null)).isZero();
        verify(documentMapper, never()).deleteById(anyLong());
        verify(termMapper, never()).delete(any());
    }

    @Test
    void shouldReindexEveryPayloadOfTheRegisteredSource() {
        stubInsertAssigningId();
        when(documentMapper.selectOne(any())).thenReturn(null);
        when(documentMapper.selectList(any())).thenReturn(List.of());

        assertThat(service.reindexAll(List.of())).isEqualTo(2);

        verify(documentMapper, times(2)).insert(any(SearchDocumentRecord.class));
        verify(termMapper, times(2)).insert(anyCollection(), eq(50));
    }

    @Test
    void shouldRemoveTheDocumentsThatTheSourceNoLongerReturns() {
        SearchDocumentRecord stale = new SearchDocumentRecord();
        stale.setId(99L);
        stale.setSourceType("article");
        stale.setSourceId("old");
        stubInsertAssigningId();
        when(documentMapper.selectOne(any())).thenReturn(null);
        when(documentMapper.selectList(any())).thenReturn(List.of(stale));

        assertThat(service.reindexAll(List.of("article"))).isEqualTo(2);

        verify(documentMapper).deleteByIds(eq(List.of(99L)));
        verify(termMapper, times(3)).delete(any());
    }

    @Test
    void shouldRemoveTheStaleDocumentsFromTheLuceneIndex() {
        SearchIndexer luceneIndexer = mock(SearchIndexer.class);
        when(luceneIndexer.usesLuceneIndex()).thenReturn(true);
        SearchDocumentService luceneService = new SearchDocumentService(documentMapper, termMapper,
                luceneIndexer, new SearchDocumentRegistry(List.of(new FixedSource())), properties);
        SearchDocumentRecord stale = new SearchDocumentRecord();
        stale.setId(99L);
        stale.setSourceType("article");
        stale.setSourceId("old");
        stubInsertAssigningId();
        when(documentMapper.selectOne(any())).thenReturn(null);
        when(documentMapper.selectList(any())).thenReturn(List.of(stale));

        assertThat(luceneService.reindexAll(List.of("article"))).isEqualTo(2);

        verify(luceneIndexer).removeByDocumentId(99L);
        verify(documentMapper).deleteByIds(eq(List.of(99L)));
    }

    @Test
    void shouldQueryBothSearchPhasesThroughTheMappers() {
        SearchDocumentRecord stored = new SearchDocumentRecord();
        stored.setId(1L);
        stored.setSourceType("article");
        stored.setSourceId("1");
        when(termMapper.selectList(any())).thenReturn(List.of(term(1L, "java", "CONTENT", 2)));
        when(documentMapper.selectList(any())).thenReturn(List.of(stored));

        assertThat(service.findTerms(List.of("java")))
                .extracting(SearchTermRow::documentId, SearchTermRow::term, SearchTermRow::weight)
                .containsExactly(tuple(1L, "java", 2));
        assertThat(service.findEnabledDocuments(List.of(1L), List.of("article")))
                .extracting(SearchDocumentSnapshot::id, SearchDocumentSnapshot::sourceType)
                .containsExactly(tuple(1L, "article"));

        verify(termMapper).selectList(any());
        verify(documentMapper).selectList(any());
    }

    @Test
    void shouldPageTheDocuments() {
        Page<SearchDocumentRecord> page = new Page<>(1L, 10L);
        page.setRecords(List.of(new SearchDocumentRecord()));
        page.setTotal(1L);
        doReturn(page).when(documentMapper).selectPage(any(), any());

        PageResult<SearchDocumentService.DocumentView> result = service.page(null, null, null, 1L, 0L);

        assertThat(result.total()).isEqualTo(1L);
        assertThat(result.records()).hasSize(1);
        assertThat(result.size()).isEqualTo(10L);
    }

    @Test
    void shouldDegradeGracefullyWithoutPersistence() {
        SearchDocumentService degraded = new SearchDocumentService(null, null, null, null, properties);

        assertThatThrownBy(() -> degraded.index(payload("article", "1", "java")))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> degraded.upsert(payload("article", "1", "java")))
                .isInstanceOf(BizException.class);
        assertThat(degraded.remove("article", "1")).isZero();
        assertThat(degraded.reindexAll(List.of())).isZero();
        assertThat(degraded.findBySource("article", "1")).isNull();
        assertThat(degraded.page(null, null, null, 1L, 0L).records()).isEmpty();
        assertThat(degraded.removeTerms(1L)).isZero();
        assertThat(degraded.insertTerms(List.of(), 50)).isZero();
        assertThat(degraded.findTerms(List.of("java"))).isEmpty();
        assertThat(degraded.findEnabledDocuments(List.of(1L), List.of())).isEmpty();
        assertThat(degraded.countEnabledDocuments()).isZero();
        assertThat(degraded.countTerms()).isZero();
        assertThat(degraded.lastIndexedAt()).isNull();
    }

    private void stubInsertAssigningId() {
        when(documentMapper.insert(any(SearchDocumentRecord.class))).thenAnswer(invocation -> {
            SearchDocumentRecord record = invocation.getArgument(0);
            record.setId(100L);
            return 1;
        });
    }

    private static SearchDocumentRecord reloadedOf(Long id) {
        SearchDocumentRecord record = new SearchDocumentRecord();
        record.setId(id);
        record.setSourceType("article");
        record.setSourceId("1");
        record.setTitle("java");
        record.setContent("java");
        return record;
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<SearchDocumentPort> provider(AtomicReference<SearchDocumentPort> port) {
        ObjectProvider<SearchDocumentPort> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenAnswer(invocation -> port.get());
        return provider;
    }

    private static SearchDocumentPayload payload(String sourceType, String sourceId, String title) {
        return new SearchDocumentPayload(sourceType, sourceId, title, null, title, null, null, null);
    }

    private static SearchIndexTermRecord term(Long documentId, String term, String field, int weight) {
        SearchIndexTermRecord record = new SearchIndexTermRecord();
        record.setDocumentId(documentId);
        record.setTerm(term);
        record.setField(field);
        record.setWeight(weight);
        return record;
    }

    private static final class FixedSource implements SearchDocumentSource {

        @Override
        public String sourceType() {
            return "article";
        }

        @Override
        public List<SearchDocumentPayload> loadAll() {
            return List.of(payload("article", "1", "java"), payload("article", "2", "spring"));
        }

        @Override
        public List<SearchDocumentPayload> loadChangedSince(Instant since) {
            return List.of();
        }
    }
}
