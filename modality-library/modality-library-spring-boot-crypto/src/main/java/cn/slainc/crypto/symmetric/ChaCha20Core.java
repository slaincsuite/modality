package cn.slainc.crypto.symmetric;

import cn.slainc.crypto.CryptoException;

public final class ChaCha20Core {

    public static final int KEY_LENGTH = 32;

    public static final int NONCE_LENGTH = 12;

    public static final int BLOCK_LENGTH = 64;

    private static final int ROUNDS = 10;

    private ChaCha20Core() {
    }

    public static void block(byte[] key, byte[] nonce, int counter, byte[] output, int offset) {
        requireKey(key);
        requireNonce(nonce);
        if (counter < 0) {
            throw new CryptoException("计数器不能为负数");
        }
        int[] state = initialState(key, nonce, counter);
        int[] working = state.clone();
        for (int round = 0; round < ROUNDS; round++) {
            quarterRound(working, 0, 4, 8, 12);
            quarterRound(working, 1, 5, 9, 13);
            quarterRound(working, 2, 6, 10, 14);
            quarterRound(working, 3, 7, 11, 15);
            quarterRound(working, 0, 5, 10, 15);
            quarterRound(working, 1, 6, 11, 12);
            quarterRound(working, 2, 7, 8, 13);
            quarterRound(working, 3, 4, 9, 14);
        }
        for (int i = 0; i < 16; i++) {
            writeInt(output, offset + i * 4, working[i] + state[i]);
        }
    }

    public static byte[] keystream(byte[] key, byte[] nonce, int counter, int length) {
        if (length < 0) {
            throw new CryptoException("长度不能为负数");
        }
        byte[] output = new byte[length];
        byte[] block = new byte[BLOCK_LENGTH];
        for (int offset = 0; offset < length; offset += BLOCK_LENGTH) {
            block(key, nonce, counter + offset / BLOCK_LENGTH, block, 0);
            System.arraycopy(block, 0, output, offset, Math.min(BLOCK_LENGTH, length - offset));
        }
        return output;
    }

    public static byte[] process(byte[] key, byte[] nonce, int counter, byte[] data) {
        if (data == null) {
            throw new CryptoException("数据不能为空");
        }
        byte[] output = new byte[data.length];
        byte[] block = new byte[BLOCK_LENGTH];
        for (int offset = 0; offset < data.length; offset += BLOCK_LENGTH) {
            block(key, nonce, counter + offset / BLOCK_LENGTH, block, 0);
            int length = Math.min(BLOCK_LENGTH, data.length - offset);
            for (int i = 0; i < length; i++) {
                output[offset + i] = (byte) (data[offset + i] ^ block[i]);
            }
        }
        return output;
    }

    private static int[] initialState(byte[] key, byte[] nonce, int counter) {
        int[] state = new int[16];
        state[0] = 0x61707865;
        state[1] = 0x3320646e;
        state[2] = 0x79622d32;
        state[3] = 0x6b206574;
        for (int i = 0; i < 8; i++) {
            state[4 + i] = readInt(key, i * 4);
        }
        state[12] = counter;
        for (int i = 0; i < 3; i++) {
            state[13 + i] = readInt(nonce, i * 4);
        }
        return state;
    }

    private static void quarterRound(int[] state, int a, int b, int c, int d) {
        state[a] += state[b];
        state[d] = Integer.rotateLeft(state[d] ^ state[a], 16);
        state[c] += state[d];
        state[b] = Integer.rotateLeft(state[b] ^ state[c], 12);
        state[a] += state[b];
        state[d] = Integer.rotateLeft(state[d] ^ state[a], 8);
        state[c] += state[d];
        state[b] = Integer.rotateLeft(state[b] ^ state[c], 7);
    }

    private static int readInt(byte[] input, int offset) {
        return (input[offset] & 0xFF)
                | (input[offset + 1] & 0xFF) << 8
                | (input[offset + 2] & 0xFF) << 16
                | (input[offset + 3] & 0xFF) << 24;
    }

    private static void writeInt(byte[] output, int offset, int value) {
        output[offset] = (byte) value;
        output[offset + 1] = (byte) (value >>> 8);
        output[offset + 2] = (byte) (value >>> 16);
        output[offset + 3] = (byte) (value >>> 24);
    }

    private static void requireKey(byte[] key) {
        if (key == null || key.length != KEY_LENGTH) {
            throw new CryptoException("ChaCha20 密钥长度必须为 " + KEY_LENGTH + " 字节");
        }
    }

    private static void requireNonce(byte[] nonce) {
        if (nonce == null || nonce.length != NONCE_LENGTH) {
            throw new CryptoException("ChaCha20 随机数长度必须为 " + NONCE_LENGTH + " 字节");
        }
    }
}
