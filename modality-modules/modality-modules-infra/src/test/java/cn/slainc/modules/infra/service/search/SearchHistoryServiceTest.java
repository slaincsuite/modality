package cn.slainc.modules.infra.service.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.slainc.modules.infra.context.CurrentUser;
import cn.slainc.modules.infra.entity.SearchHistoryRecord;
import cn.slainc.modules.infra.mapper.SearchHistoryMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SearchHistoryServiceTest {

    private static final Long USER_ID = 7L;

    private static final long NOW = System.currentTimeMillis();

    private final SearchHistoryMapper searchHistoryMapper = mock(SearchHistoryMapper.class);

    private final CurrentUser currentUser = mock(CurrentUser.class);

    private final SearchHistoryService service = new SearchHistoryService(searchHistoryMapper, currentUser);

    @BeforeAll
    static void initializeLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SearchHistoryRecord.class);
    }

    @Test
    void shouldRecordANewKeywordForTheCurrentAccount() {
        when(currentUser.userId()).thenReturn(USER_ID);
        when(searchHistoryMapper.selectOne(any())).thenReturn(null);

        service.record("  java  ");

        ArgumentCaptor<SearchHistoryRecord> captor = ArgumentCaptor.forClass(SearchHistoryRecord.class);
        verify(searchHistoryMapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getKeyword()).isEqualTo("java");
        assertThat(captor.getValue().getSearchedAt()).isNotNull();
        verify(searchHistoryMapper, never()).updateById(any(SearchHistoryRecord.class));
    }

    @Test
    void shouldRefreshOnlyTheSearchTimeOfAnExistingKeyword() {
        when(currentUser.userId()).thenReturn(USER_ID);
        SearchHistoryRecord stored = history(1L, "java", NOW - 60000L);
        when(searchHistoryMapper.selectOne(any())).thenReturn(stored);
        when(searchHistoryMapper.updateById(stored)).thenReturn(1);

        service.record("java");

        assertThat(stored.getKeyword()).isEqualTo("java");
        assertThat(stored.getSearchedAt()).isGreaterThan(NOW - 60000L);
        verify(searchHistoryMapper, never()).insert(any(SearchHistoryRecord.class));
    }

    @Test
    void shouldIgnoreABlankKeyword() {
        when(currentUser.userId()).thenReturn(USER_ID);

        service.record(null);
        service.record("   ");

        verifyNoInteractions(searchHistoryMapper);
    }

    @Test
    void shouldTruncateAnOverlongKeywordToTheColumnWidth() {
        when(currentUser.userId()).thenReturn(USER_ID);
        when(searchHistoryMapper.selectOne(any())).thenReturn(null);

        service.record("k".repeat(200));

        ArgumentCaptor<SearchHistoryRecord> captor = ArgumentCaptor.forClass(SearchHistoryRecord.class);
        verify(searchHistoryMapper).insert(captor.capture());
        assertThat(captor.getValue().getKeyword()).hasSize(128);
    }

    @Test
    void shouldDropTheEntriesBeyondTheKeptLimit() {
        when(currentUser.userId()).thenReturn(USER_ID);
        when(searchHistoryMapper.selectOne(any())).thenReturn(null);
        List<SearchHistoryRecord> histories = new ArrayList<>();
        for (int i = 0; i <= 50; i++) {
            histories.add(history(100L + i, "keyword-" + i, NOW - i * 60000L));
        }
        when(searchHistoryMapper.selectList(any())).thenReturn(histories);

        service.record("java");

        verify(searchHistoryMapper).deleteByIds(List.of(150L));
    }

    @Test
    void shouldReturnTheLatestKeywordsWithACappedLimit() {
        when(currentUser.userId()).thenReturn(USER_ID);
        when(searchHistoryMapper.selectList(any()))
                .thenReturn(List.of(history(1L, "java", NOW), history(2L, "spring", NOW - 1000L)));

        assertThat(service.list(null)).extracting(SearchHistoryService.HistoryView::keyword)
                .containsExactly("java", "spring");
        assertThat(service.list(999)).hasSize(2);

        ArgumentCaptor<Wrapper<SearchHistoryRecord>> captor = wrapperCaptor();
        verify(searchHistoryMapper, times(2)).selectList(captor.capture());
        assertThat(captor.getAllValues().get(0).getSqlSegment())
                .contains("user_id")
                .contains("ORDER BY searched_at DESC")
                .contains("limit 10");
        assertThat(captor.getAllValues().get(1).getSqlSegment()).contains("limit 50");
        assertThat(wrapperParams(captor.getAllValues().get(1))).containsExactly(USER_ID);
    }

    @Test
    void shouldClearOnlyTheHistoryOfTheCurrentAccount() {
        when(currentUser.userId()).thenReturn(USER_ID);
        when(searchHistoryMapper.delete(any())).thenReturn(4);

        assertThat(service.clear()).isEqualTo(4);

        ArgumentCaptor<Wrapper<SearchHistoryRecord>> captor = wrapperCaptor();
        verify(searchHistoryMapper).delete(captor.capture());
        assertThat(captor.getValue().getSqlSegment()).contains("user_id");
        assertThat(wrapperParams(captor.getValue())).containsExactly(USER_ID);
    }

    @Test
    void shouldIgnoreReadsAndWritesWithoutALogin() {
        when(currentUser.userId()).thenReturn(null);

        assertThat(service.list(null)).isEmpty();
        assertThat(service.clear()).isZero();
        service.record("java");

        verifyNoInteractions(searchHistoryMapper);
    }

    private static SearchHistoryRecord history(Long id, String keyword, long searchedAt) {
        SearchHistoryRecord record = new SearchHistoryRecord();
        record.setId(id);
        record.setUserId(USER_ID);
        record.setKeyword(keyword);
        record.setSearchedAt(searchedAt);
        return record;
    }

    // Lambda 包装器的参数是延迟写入的：先取一次 SQL 段，参数表才会被填充
    private static Collection<Object> wrapperParams(Wrapper<SearchHistoryRecord> wrapper) {
        assertThat(wrapper.getSqlSegment()).isNotBlank();
        return ((AbstractWrapper<?, ?, ?>) wrapper).getParamNameValuePairs().values();
    }

    private static ArgumentCaptor<Wrapper<SearchHistoryRecord>> wrapperCaptor() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<SearchHistoryRecord>> captor = ArgumentCaptor.forClass(Wrapper.class);
        return captor;
    }
}
