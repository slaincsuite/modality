package cn.slainc.core.page;

import cn.slainc.core.entity.BaseQuery;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageModelTest {

    @Test
    void normalizesPageParam() {
        PageParam defaults = PageParam.defaults();
        assertEquals(1L, defaults.current());
        assertEquals(PageParam.DEFAULT_SIZE, defaults.size());
        assertEquals(0L, defaults.offset());
        assertFalse(defaults.hasSort());

        PageParam normalized = PageParam.of(0L, 0L).normalized();
        assertEquals(1L, normalized.current());
        assertEquals(PageParam.DEFAULT_SIZE, normalized.size());

        assertEquals(PageParam.MAX_SIZE, PageParam.of(1L, 100000L).normalized().size());
        assertEquals(20L, PageParam.of(3L, 10L).offset());
    }

    @Test
    void sortDirections() {
        assertTrue(Sort.desc("createdAt").descending());
        assertFalse(Sort.asc("id").descending());
        assertEquals(Sort.Direction.ASC, Sort.of("id", null).direction());
        assertEquals(Sort.Direction.DESC, Sort.of("id", Sort.Direction.DESC).direction());
    }

    @Test
    void pageParamWithSort() {
        PageParam param = PageParam.of(1L, 10L).withSort(Sort.asc("id"));
        assertTrue(param.hasSort());
        assertEquals(1, param.sorts().size());
    }

    @Test
    void cursorPagination() {
        Cursor first = Cursor.first("id", 0).normalized();
        assertEquals(Cursor.DEFAULT_SIZE, first.size());
        assertTrue(first.isFirstPage());
        assertTrue(first.forward());
        assertEquals(Cursor.MAX_SIZE, Cursor.first("id", 9999).normalized().size());
        assertFalse(new Cursor("id", 5L, Cursor.Direction.PREVIOUS, 10).forward());
        assertFalse(new Cursor("id", 5L, null, 10).isFirstPage());
    }

    @Test
    void baseQueryConvertsToPageParam() {
        BaseQuery query = new BaseQuery();
        query.setPageNum(2L);
        query.setPageSize(20L);
        query.setOrderBy("createdAt");
        query.setOrderDirection("DESC");

        PageParam param = query.toPageParam();
        assertEquals(2L, param.current());
        assertEquals(20L, param.size());
        assertEquals(1, param.sorts().size());
        assertTrue(param.sorts().get(0).descending());

        BaseQuery plain = new BaseQuery();
        assertFalse(plain.toPageParam().hasSort());
        assertTrue(plain.toPageParam().normalized().current() == 1L);
    }
}
