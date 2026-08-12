package cn.slainc.utils.codec;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodecTest {

    @Test
    void base64RoundTrip() {
        String text = "modality 中文";
        assertEquals(text, Base64Utils.decodeToString(Base64Utils.encode(text)));
        assertTrue(Base64Utils.isBase64(Base64Utils.encode(text)));
        String urlSafe = Base64Utils.encodeUrlSafe(text);
        assertEquals(text, Base64Utils.decodeUrlSafeToString(urlSafe));
        assertEquals("aGVsbG8=", Base64Utils.encode("hello"));
    }

    @Test
    void hexRoundTrip() {
        byte[] bytes = "hello".getBytes(StandardCharsets.UTF_8);
        assertEquals("68656c6c6f", HexUtils.encode(bytes));
        assertEquals("68656C6C6F", HexUtils.encodeUpper(bytes));
        assertArrayEquals(bytes, HexUtils.decode("68656c6c6f"));
        assertEquals("hello", HexUtils.decodeToString("68656c6c6f"));
        assertTrue(HexUtils.isHex("68656c6c6f"));
        assertThrows(IllegalArgumentException.class, () -> HexUtils.decode("abc"));
    }

    @Test
    void base58RoundTrip() {
        byte[] bytes = "hello world".getBytes(StandardCharsets.UTF_8);
        String encoded = Base58Utils.encode(bytes);
        assertArrayEquals(bytes, Base58Utils.decode(encoded));
        assertEquals("1", Base58Utils.encode(new byte[]{0}));
        assertEquals("", Base58Utils.encode(new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> Base58Utils.decode("0OIl"));
    }

    @Test
    void urlCodec() {
        assertEquals("a+b", UrlCodecUtils.encode("a b"));
        assertEquals("a b", UrlCodecUtils.decode("a+b"));
        assertEquals("a%20b", UrlCodecUtils.encodePathSegment("a b"));
        assertEquals("中文", UrlCodecUtils.decode(UrlCodecUtils.encode("中文")));
    }
}
