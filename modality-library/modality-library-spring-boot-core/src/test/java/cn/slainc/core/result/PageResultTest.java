package cn.slainc.core.result;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageResultTest {

    @Test
    void computesPagesAndFlags() {
        PageResult<String> page = PageResult.of(List.of("a", "b"), 25L, 2L, 10L);
        assertEquals(3L, page.pages());
        assertEquals(2L, page.current());
        assertEquals(25L, page.total());
        assertTrue(page.hasNext());
        assertFalse(page.isEmpty());
    }

    @Test
    void lastPageHasNoNext() {
        PageResult<String> page = PageResult.of(List.of("a"), 25L, 3L, 10L);
        assertFalse(page.hasNext());
    }

    @Test
    void handlesEmptyAndZeroSize() {
        PageResult<String> empty = PageResult.empty(1L, 10L);
        assertEquals(0L, empty.pages());
        assertTrue(empty.isEmpty());
        assertFalse(empty.hasNext());

        PageResult<String> zeroSize = PageResult.of(null, 10L, 0L, 0L);
        assertEquals(1L, zeroSize.current());
        assertEquals(0L, zeroSize.size());
        assertEquals(0L, zeroSize.pages());
        assertTrue(zeroSize.isEmpty());
    }

    @Test
    void slicesInMemoryList() {
        PageResult<Integer> page = PageResult.from(List.of(1, 2, 3, 4, 5), 2L, 2L);
        assertEquals(List.of(3, 4), page.records());
        assertEquals(5L, page.total());
        assertEquals(3L, page.pages());

        assertEquals(List.of(), PageResult.from(List.of(), 1L, 10L).records());
        assertTrue(PageResult.from(List.of(1, 2, 3, 4, 5), 99L, 2L).records().isEmpty());
    }

    @Test
    void mapsRecords() {
        PageResult<String> mapped = PageResult.of(List.of("a", "b"), 2L, 1L, 10L).map(String::toUpperCase);
        assertEquals(List.of("A", "B"), mapped.records());
        assertEquals(2L, mapped.total());
    }
}
