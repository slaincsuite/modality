package cn.slainc.utils.reflect;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeanUtilsTest {

    static class Source {
        private String name = "modality";
        private int age = 3;
    }

    static class Target {
        private String name;
        private int age;
        private String extra = "keep";
    }

    static class Holder {
        private List<String> items = new ArrayList<>(List.of("a", "b"));
        private Source source = new Source();
    }

    @Test
    void copyPropertiesKeepsUntouchedFields() {
        Target target = new Target();
        BeanUtils.copyProperties(new Source(), target);
        assertEquals("modality", target.name);
        assertEquals(3, target.age);
        assertEquals("keep", target.extra);
    }

    @Test
    void copyPropertiesByType() {
        Target target = BeanUtils.copyProperties(new Source(), Target.class);
        assertEquals("modality", target.name);
        assertEquals(3, target.age);
    }

    @Test
    void beanToMapAndBack() {
        Map<String, Object> map = BeanUtils.beanToMap(new Source());
        assertEquals("modality", map.get("name"));
        assertEquals(3, map.get("age"));
        Source restored = BeanUtils.mapToBean(map, Source.class);
        assertEquals("modality", restored.name);
        assertEquals(3, restored.age);

        Source withNull = new Source();
        withNull.name = null;
        Map<String, Object> filtered = BeanUtils.beanToMap(withNull, true);
        assertTrue(!filtered.containsKey("name"));
        assertTrue(filtered.containsKey("age"));
        assertEquals(2, BeanUtils.beanToMap(withNull).size());
    }

    @Test
    void diffDetectsChanges() {
        Source left = new Source();
        Source right = new Source();
        assertEquals(0, BeanUtils.diff(left, right).size());
        right.age = 9;
        List<BeanUtils.FieldDiff> diffs = BeanUtils.diff(left, right);
        assertEquals(1, diffs.size());
        assertEquals("age", diffs.get(0).field());
        assertEquals(3, diffs.get(0).left());
        assertEquals(9, diffs.get(0).right());
    }

    @Test
    void deepCopyDetachesNestedState() {
        Holder holder = new Holder();
        Holder copy = BeanUtils.deepCopy(holder);
        assertNotSame(holder.items, copy.items);
        assertNotSame(holder.source, copy.source);
        assertEquals(holder.items, copy.items);
        assertEquals(holder.source.name, copy.source.name);
        copy.items.add("c");
        assertEquals(2, holder.items.size());
        assertTrue(BeanUtils.deepCopy((Holder) null) == null);
    }
}
