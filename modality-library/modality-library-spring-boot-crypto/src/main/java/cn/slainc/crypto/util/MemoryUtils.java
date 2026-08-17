package cn.slainc.crypto.util;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.Arrays;

public final class MemoryUtils {

    private MemoryUtils() {
    }

    public static void zero(byte[] bytes) {
        if (bytes != null) {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    public static void zero(char[] chars) {
        if (chars != null) {
            Arrays.fill(chars, '\0');
        }
    }

    public static void zero(ByteBuffer buffer) {
        if (buffer == null) {
            return;
        }
        ByteBuffer duplicate = buffer.duplicate();
        duplicate.clear();
        while (duplicate.hasRemaining()) {
            duplicate.put((byte) 0);
        }
    }

    public static void zero(CharBuffer buffer) {
        if (buffer == null) {
            return;
        }
        CharBuffer duplicate = buffer.duplicate();
        duplicate.clear();
        while (duplicate.hasRemaining()) {
            duplicate.put('\0');
        }
    }

    public static byte[] copy(byte[] bytes) {
        return bytes == null ? null : Arrays.copyOf(bytes, bytes.length);
    }
}
