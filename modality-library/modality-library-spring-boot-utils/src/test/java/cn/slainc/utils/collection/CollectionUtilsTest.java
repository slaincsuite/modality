package cn.slainc.utils.collection;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollectionUtilsTest {

    @Test
    void emptiness() {
        assertTrue(CollectionUtils.isEmpty(null));
        assertTrue(CollectionUtils.isEmpty(List.of()));
        assertFalse(CollectionUtils.isEmpty(List.of(1)));
        assertTrue(CollectionUtils.isAnyEmpty(List.of(), List.of(1)));
        assertFalse(CollectionUtils.isAnyEmpty(List.of(1), List.of(2)));
    }

    @Test
    void groupAndDistinct() {
        List<String> items = List.of("aa", "ab", "ba");
        Map<Character, List<String>> grouped = CollectionUtils.groupBy(items, s -> s.charAt(0));
        assertEquals(2, grouped.size());
        assertEquals(List.of("aa", "ab"), grouped.get('a'));

        List<String> distinct = CollectionUtils.distinctBy(items, s -> s.charAt(0));
        assertEquals(List.of("aa", "ba"), distinct);
        assertEquals(List.of("aa", "ab", "ba"), CollectionUtils.distinct(List.of("aa", "aa", "ab", "ba")));
    }

    @Test
    void setOperations() {
        assertEquals(List.of(1, 2, 3, 4), CollectionUtils.union(List.of(1, 2), List.of(3, 4)));
        assertEquals(List.of(2, 3), CollectionUtils.intersection(List.of(1, 2, 3), List.of(2, 3, 4)));
        assertEquals(List.of(1), CollectionUtils.difference(List.of(1, 2, 3), List.of(2, 3)));
    }

    @Test
    void partitionAndFlatten() {
        List<List<Integer>> partitions = CollectionUtils.partition(List.of(1, 2, 3, 4, 5), 2);
        assertEquals(3, partitions.size());
        assertEquals(List.of(1, 2), partitions.get(0));
        assertEquals(List.of(5), partitions.get(2));
        assertEquals(List.of(1, 2, 3), CollectionUtils.flatten(List.of(List.of(1), List.of(2, 3))));
    }

    @Test
    void accessors() {
        assertEquals(1, CollectionUtils.first(List.of(1, 2)));
        assertEquals(2, CollectionUtils.last(List.of(1, 2)));
        assertTrue(CollectionUtils.isEmpty(CollectionUtils.emptyIfNull(null)));
    }
}
