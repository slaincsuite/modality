package cn.slainc.web.log;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SensitiveMaskerTest {

    @Test
    void detectsSensitiveKeys() {
        assertTrue(SensitiveMasker.isSensitiveKey("password"));
        assertTrue(SensitiveMasker.isSensitiveKey("accessToken"));
        assertTrue(SensitiveMasker.isSensitiveKey("access_token"));
        assertTrue(SensitiveMasker.isSensitiveKey("X-Api-Secret"));
        assertTrue(SensitiveMasker.isSensitiveKey("idCard"));
        assertTrue(SensitiveMasker.isSensitiveKey("mobile"));
        assertFalse(SensitiveMasker.isSensitiveKey("username"));
        assertFalse(SensitiveMasker.isSensitiveKey(null));
    }

    @Test
    void masksMaps() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("username", "bob");
        source.put("password", "secret-value");
        Map<String, Object> masked = SensitiveMasker.mask(source);
        assertEquals("bob", masked.get("username"));
        assertEquals(SensitiveMasker.MASK, masked.get("password"));
        assertTrue(SensitiveMasker.mask(null).isEmpty());
    }

    @Test
    void masksQueryString() {
        assertEquals("username=bob&password=" + SensitiveMasker.MASK,
                SensitiveMasker.maskQueryString("username=bob&password=p@ss"));
        assertEquals("token=" + SensitiveMasker.MASK + "&page=1",
                SensitiveMasker.maskQueryString("token=abc&page=1"));
        assertEquals("flag", SensitiveMasker.maskQueryString("flag"));
    }

    @Test
    void truncatesLongPayload() {
        String text = "x".repeat(2000);
        String truncated = SensitiveMasker.truncate(text, 100);
        assertTrue(truncated.startsWith("x".repeat(100)));
        assertTrue(truncated.contains("已截断"));
        assertEquals("short", SensitiveMasker.truncate("short", 100));
    }
}
