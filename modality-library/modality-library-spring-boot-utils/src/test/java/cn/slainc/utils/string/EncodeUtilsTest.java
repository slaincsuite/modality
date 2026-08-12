package cn.slainc.utils.string;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EncodeUtilsTest {

    @Test
    void unicodeRoundTrip() {
        String original = "modality 中文";
        String unicode = EncodeUtils.toUnicode(original);
        assertTrue(unicode.startsWith("\\u"));
        assertEquals(original, EncodeUtils.fromUnicode(unicode));
    }

    @Test
    void gbkRoundTrip() {
        String original = "中文测试";
        assertEquals(original, EncodeUtils.fromGbk(EncodeUtils.toGbk(original)));
    }

    @Test
    void utf8Bytes() {
        byte[] bytes = EncodeUtils.toUtf8Bytes("abc");
        assertEquals(3, bytes.length);
        assertEquals("abc", EncodeUtils.fromUtf8Bytes(bytes));
    }
}
