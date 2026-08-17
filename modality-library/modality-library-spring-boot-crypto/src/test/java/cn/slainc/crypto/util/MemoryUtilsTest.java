package cn.slainc.crypto.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import org.junit.jupiter.api.Test;

class MemoryUtilsTest {

    @Test
    void zeroesByteArrays() {
        byte[] bytes = {1, 2, 3};
        MemoryUtils.zero(bytes);
        assertArrayEquals(new byte[3], bytes);
        MemoryUtils.zero((byte[]) null);
    }

    @Test
    void zeroesCharArrays() {
        char[] chars = "secret".toCharArray();
        MemoryUtils.zero(chars);
        assertArrayEquals(new char[6], chars);
        MemoryUtils.zero((char[]) null);
    }

    @Test
    void zeroesBuffers() {
        ByteBuffer bytes = ByteBuffer.wrap(new byte[]{1, 2, 3});
        MemoryUtils.zero(bytes);
        assertArrayEquals(new byte[3], bytes.array());
        CharBuffer chars = CharBuffer.wrap("abc".toCharArray());
        MemoryUtils.zero(chars);
        assertEquals('\0', chars.get(0));
        MemoryUtils.zero((ByteBuffer) null);
        MemoryUtils.zero((CharBuffer) null);
    }

    @Test
    void copiesArrays() {
        byte[] source = {1, 2, 3};
        byte[] copy = MemoryUtils.copy(source);
        assertArrayEquals(source, copy);
        assertNotSame(source, copy);
        assertNull(MemoryUtils.copy(null));
    }
}
