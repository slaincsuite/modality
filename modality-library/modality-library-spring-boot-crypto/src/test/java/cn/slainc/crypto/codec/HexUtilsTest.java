package cn.slainc.crypto.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.slainc.crypto.CryptoException;
import org.junit.jupiter.api.Test;

class HexUtilsTest {

    @Test
    void encodesLowerAndUpperCase() {
        byte[] bytes = {(byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF, 0x00, 0x7F};
        assertEquals("deadbeef007f", HexUtils.encode(bytes));
        assertEquals("DEADBEEF007F", HexUtils.encodeUpper(bytes));
        assertEquals("DEADBEEF007F", HexUtils.encode(bytes, true));
    }

    @Test
    void encodesKnownVectors() {
        assertEquals("616263", HexUtils.encode("abc".getBytes()));
        assertEquals("", HexUtils.encode(new byte[0]));
        assertNull(HexUtils.encode(null));
    }

    @Test
    void decodesWithPrefixAndMixedCase() {
        assertArrayEquals(new byte[]{(byte) 0xAB, (byte) 0xCD}, HexUtils.decode("0xAbCd"));
        assertArrayEquals(new byte[]{0x01, 0x02}, HexUtils.decode(" 0102 "));
        assertNull(HexUtils.decode(null));
    }

    @Test
    void roundTripsAllByteValues() {
        byte[] bytes = new byte[256];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) i;
        }
        assertArrayEquals(bytes, HexUtils.decode(HexUtils.encode(bytes)));
        assertArrayEquals(bytes, HexUtils.decode(HexUtils.encodeUpper(bytes)));
    }

    @Test
    void rejectsIllegalInput() {
        assertThrows(CryptoException.class, () -> HexUtils.decode("abc"));
        assertThrows(CryptoException.class, () -> HexUtils.decode("zz"));
        assertThrows(CryptoException.class, () -> HexUtils.decode("0x0g"));
    }

    @Test
    void detectsHexText() {
        assertTrue(HexUtils.isHex("0xDEADBEEF"));
        assertTrue(HexUtils.isHex("0123"));
        assertFalse(HexUtils.isHex("xyz"));
        assertFalse(HexUtils.isHex(""));
        assertFalse(HexUtils.isHex(null));
    }
}
