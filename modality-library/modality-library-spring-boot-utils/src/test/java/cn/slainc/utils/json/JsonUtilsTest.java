package cn.slainc.utils.json;

import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonUtilsTest {

    @Test
    void serializationRoundTrip() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("name", "modality");
        source.put("count", 3);
        String json = JsonUtils.toJson(source);
        assertEquals("{\"name\":\"modality\",\"count\":3}", json);
        Map<String, Object> parsed = JsonUtils.parseMap(json);
        assertEquals("modality", parsed.get("name"));
        assertEquals(3, parsed.get("count"));
    }

    @Test
    void typedConversion() {
        assertEquals("modality", JsonUtils.parse("{\"name\":\"modality\"}", Map.class).get("name"));
        List<Integer> list = JsonUtils.parseList("[1,2,3]", Integer.class);
        assertEquals(3, list.size());
        assertEquals(3, list.get(2));
        assertNull(JsonUtils.parse(null, Map.class));
        assertNull(JsonUtils.toJson(null));
    }

    @Test
    void pathExtraction() {
        String json = "{\"a\":{\"b\":[10,20]},\"c\":\"x\"}";
        assertEquals(20, (int) JsonUtils.<Integer>path(json, "a.b[1]"));
        assertEquals("x", JsonUtils.<String>path(json, "c"));
        assertNull(JsonUtils.pathNode(json, "a.missing"));
        assertEquals(2, JsonUtils.pathNode(json, "a.b").size());
    }

    @Test
    void typedPathConversion() {
        String json = "{\"data\":{\"id\":9007199254740993,\"name\":\"modality\"},\"tags\":[\"a\",\"b\"]}";

        PathDto data = JsonUtils.path(json, "data", PathDto.class);
        assertEquals(9007199254740993L, data.id());
        assertEquals("modality", data.name());
        assertEquals(9007199254740993L, JsonUtils.path(json, "data.id", Long.class));
        assertEquals(List.of("a", "b"), JsonUtils.path(json, "tags", new TypeReference<List<String>>() {
        }));
        assertNull(JsonUtils.path(json, "data.missing", Long.class));
    }

    record PathDto(long id, String name) {
    }

    @Test
    void mergeAndCompare() {
        String left = "{\"a\":1,\"nested\":{\"x\":1}}";
        String right = "{\"b\":2,\"nested\":{\"y\":2}}";
        String merged = JsonUtils.merge(left, right);
        Map<String, Object> parsed = JsonUtils.parseMap(merged);
        assertEquals(1, parsed.get("a"));
        assertEquals(2, parsed.get("b"));
        assertEquals(1, JsonUtils.<Integer>path(merged, "nested.x"));
        assertEquals(2, JsonUtils.<Integer>path(merged, "nested.y"));

        Map<String, Object> diffs = JsonUtils.compare("{\"a\":1}", "{\"a\":2}");
        assertEquals(1, diffs.size());
        assertTrue(diffs.containsKey("a"));
    }

    @Test
    void flattenAndValidate() {
        Map<String, Object> flat = JsonUtils.flatten("{\"a\":{\"b\":1},\"c\":[1,2]}");
        assertEquals(1, flat.get("a.b"));
        assertEquals(1, flat.get("c[0]"));
        assertTrue(JsonUtils.isValid("{\"a\":1}"));
        assertFalse(JsonUtils.isValid("{invalid"));
    }
}
