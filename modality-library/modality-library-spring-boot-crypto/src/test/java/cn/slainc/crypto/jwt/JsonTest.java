package cn.slainc.crypto.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cn.slainc.crypto.CryptoException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonTest {

    @Test
    void writesPrimitivesAndStructures() {
        assertEquals("null", Json.write(null));
        assertEquals("true", Json.write(true));
        assertEquals("42", Json.write(42L));
        assertEquals("1.5", Json.write(1.5d));
        assertEquals("\"text\"", Json.write("text"));
        assertEquals("[1,2,3]", Json.write(List.of(1, 2, 3)));
        assertEquals("{\"a\":1,\"b\":\"x\"}", Json.write(ordered(Map.of("a", 1, "b", "x"), "a", "b")));
    }

    @Test
    void escapesSpecialCharacters() {
        assertEquals("\"a\\\"b\"", Json.write("a\"b"));
        assertEquals("\"a\\\\b\"", Json.write("a\\b"));
        assertEquals("\"line\\nbreak\"", Json.write("line\nbreak"));
        assertEquals("\"tab\\t\"", Json.write("tab\t"));
        assertEquals("\"控制\"", Json.write("控制"));
    }

    @Test
    void parsesNestedDocuments() {
        Map<String, Object> values = ordered(Map.of("iss", "modality", "exp", 1300819380L, "admin", true, "none", "x"), "iss", "exp", "admin", "none");
        values.put("none", null);
        values.put("roles", List.of("a", "b"));
        String json = Json.write(values);
        Object parsed = Json.parse(json);
        Map<?, ?> map = assertInstanceOf(Map.class, parsed);
        assertEquals("modality", map.get("iss"));
        assertEquals(1300819380L, map.get("exp"));
        assertEquals(true, map.get("admin"));
        assertNull(map.get("none"));
        assertEquals(List.of("a", "b"), map.get("roles"));
        assertEquals(1.5d, Json.parse("1.5"));
        assertEquals("x", Json.parse("  \"x\"  "));
        assertEquals(Map.of(), Json.parse("{}"));
        assertEquals(List.of(), Json.parse("[]"));
    }

    @Test
    void parsesUnicodeEscapes() {
        assertEquals("中", Json.parse("\"\\u4e2d\""));
        assertEquals(12345678901234L, Json.parse("12345678901234"));
    }

    @Test
    void rejectsIllegalDocuments() {
        assertThrows(CryptoException.class, () -> Json.parse(null));
        assertThrows(CryptoException.class, () -> Json.parse("{} {}"));
        assertThrows(CryptoException.class, () -> Json.parse("{"));
        assertThrows(CryptoException.class, () -> Json.parse("\"unterminated"));
        assertThrows(CryptoException.class, () -> Json.parse("tru"));
        assertThrows(CryptoException.class, () -> Json.parse("{a:1}"));
        assertThrows(CryptoException.class, () -> Json.parse("[1,]"));
        assertThrows(CryptoException.class, () -> Json.parse("nul"));
        assertThrows(CryptoException.class, () -> Json.parse("\"\\uZZZZ\""));
        assertThrows(CryptoException.class, () -> Json.write(Double.NaN));
    }

    private static Map<String, Object> ordered(Map<String, Object> values, String... order) {
        Map<String, Object> ordered = new LinkedHashMap<>();
        for (String name : order) {
            if (values.containsKey(name)) {
                ordered.put(name, values.get(name));
            }
        }
        return ordered;
    }
}
