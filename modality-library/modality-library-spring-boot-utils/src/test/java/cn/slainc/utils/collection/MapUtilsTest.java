package cn.slainc.utils.collection;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapUtilsTest {

    @Test
    void nestedGet() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("b", Map.of("c", 42));
        Map<String, Object> root = Map.of("a", nested);
        assertEquals(42, (int) MapUtils.<Integer>get(root, "a.b.c"));
        assertNull(MapUtils.get(root, "a.x.c"));
        assertEquals("fallback", MapUtils.get(root, "missing", "fallback"));
    }

    @Test
    void nestedPut() {
        Map<String, Object> map = new LinkedHashMap<>();
        MapUtils.put(map, "a.b.c", 1);
        assertEquals(1, (int) MapUtils.<Integer>get(map, "a.b.c"));
    }

    @Test
    void deepMerge() {
        Map<String, Object> left = new LinkedHashMap<>();
        left.put("a", 1);
        left.put("nested", new LinkedHashMap<>(Map.of("x", 1)));
        Map<String, Object> right = new LinkedHashMap<>();
        right.put("b", 2);
        right.put("nested", new LinkedHashMap<>(Map.of("y", 2)));
        Map<String, Object> merged = MapUtils.deepMerge(left, right);
        assertEquals(1, merged.get("a"));
        assertEquals(2, merged.get("b"));
        assertEquals(1, (int) MapUtils.<Integer>get(merged, "nested.x"));
        assertEquals(2, (int) MapUtils.<Integer>get(merged, "nested.y"));
    }

    @Test
    void sortAndFlatten() {
        Map<String, Integer> unsorted = new LinkedHashMap<>();
        unsorted.put("b", 2);
        unsorted.put("a", 1);
        assertEquals("a", MapUtils.sortByKey(unsorted).keySet().iterator().next());
        assertEquals("a", MapUtils.sortByValue(unsorted).keySet().iterator().next());

        Map<String, Object> flatSource = new LinkedHashMap<>();
        flatSource.put("a", Map.of("b", 1));
        Map<String, Object> flat = MapUtils.flatten(flatSource);
        assertTrue(flat.containsKey("a.b"));
    }

    @Test
    void invert() {
        Map<String, Integer> source = Map.of("a", 1);
        assertEquals("a", MapUtils.invert(source).get(1));
    }
}
