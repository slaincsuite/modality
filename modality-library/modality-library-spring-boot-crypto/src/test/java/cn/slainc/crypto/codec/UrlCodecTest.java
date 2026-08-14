package cn.slainc.crypto.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class UrlCodecTest {

    @Test
    void encodesAndDecodesFormValues() {
        assertEquals("a+b", UrlCodec.encode("a b"));
        assertEquals("a b", UrlCodec.decode("a+b"));
        assertEquals("%E4%B8%AD%E6%96%87+%E7%A9%BA%E6%A0%BC", UrlCodec.encode("中文 空格"));
        assertEquals("中文 空格", UrlCodec.decode("%E4%B8%AD%E6%96%87+%E7%A9%BA%E6%A0%BC"));
    }

    @Test
    void encodesPathSegments() {
        assertEquals("a%20b", UrlCodec.encodePathSegment("a b"));
        assertEquals("a b", UrlCodec.decodePathSegment("a%20b"));
        assertEquals("a+b", UrlCodec.decodePathSegment("a%2Bb"));
        assertEquals("/a/b", UrlCodec.decodePathSegment("%2Fa%2Fb"));
    }

    @Test
    void keepsReservedCharactersReadable() {
        assertEquals("a%2Fb", UrlCodec.encode("a/b"));
        assertEquals("a/b", UrlCodec.decode("a%2Fb"));
    }

    @Test
    void handlesNull() {
        assertNull(UrlCodec.encode(null));
        assertNull(UrlCodec.decode(null));
        assertNull(UrlCodec.encodePathSegment(null));
    }
}
