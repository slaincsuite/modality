package cn.slainc.crypto.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class Base64UtilsTest {

    @Test
    void encodesRfc4648Vectors() {
        assertEquals("", Base64Utils.encode(""));
        assertEquals("Zg==", Base64Utils.encode("f"));
        assertEquals("Zm8=", Base64Utils.encode("fo"));
        assertEquals("Zm9v", Base64Utils.encode("foo"));
        assertEquals("Zm9vYg==", Base64Utils.encode("foob"));
        assertEquals("Zm9vYmE=", Base64Utils.encode("fooba"));
        assertEquals("Zm9vYmFy", Base64Utils.encode("foobar"));
    }

    @Test
    void decodesRfc4648Vectors() {
        assertEquals("f", Base64Utils.decodeToString("Zg=="));
        assertEquals("foobar", Base64Utils.decodeToString("Zm9vYmFy"));
        assertArrayEquals("foo".getBytes(StandardCharsets.UTF_8), Base64Utils.decode("Zm9v"));
    }

    @Test
    void encodesUrlSafeAlphabet() {
        byte[] bytes = {(byte) 0xFB, (byte) 0xFF, (byte) 0xBF};
        assertEquals("+/+/", Base64Utils.encode(bytes));
        assertEquals("-_-_", Base64Utils.encodeUrl(bytes));
        assertEquals("-_8", Base64Utils.encodeUrlNoPadding(new byte[]{(byte) 0xFB, (byte) 0xFF}));
        assertArrayEquals(new byte[]{(byte) 0xFB, (byte) 0xFF}, Base64Utils.decodeUrl("-_8"));
        assertArrayEquals(bytes, Base64Utils.decodeUrl(Base64Utils.encodeUrl(bytes)));
    }

    @Test
    void encodesChineseText() {
        String text = "国密算法";
        assertEquals(text, Base64Utils.decodeToString(Base64Utils.encode(text)));
    }

    @Test
    void handlesNullAndIllegalInput() {
        assertNull(Base64Utils.encode((byte[]) null));
        assertNull(Base64Utils.decode(null));
        assertThrows(IllegalArgumentException.class, () -> Base64Utils.decodeUrl("abcde"));
    }
}
