package cn.slainc.crypto.symmetric;

import cn.slainc.crypto.random.SecureRandomUtils;

public final class ChaCha20Utils {

    public static final int DEFAULT_COUNTER = 1;

    private ChaCha20Utils() {
    }

    public static byte[] generateKey() {
        return SecureRandomUtils.nextBytes(ChaCha20Core.KEY_LENGTH);
    }

    public static byte[] generateNonce() {
        return SecureRandomUtils.nextBytes(ChaCha20Core.NONCE_LENGTH);
    }

    public static byte[] encrypt(byte[] key, byte[] nonce, byte[] data) {
        return ChaCha20Core.process(key, nonce, DEFAULT_COUNTER, data);
    }

    public static byte[] decrypt(byte[] key, byte[] nonce, byte[] data) {
        return ChaCha20Core.process(key, nonce, DEFAULT_COUNTER, data);
    }

    public static byte[] process(byte[] key, byte[] nonce, int counter, byte[] data) {
        return ChaCha20Core.process(key, nonce, counter, data);
    }

    public static byte[] keystream(byte[] key, byte[] nonce, int counter, int length) {
        return ChaCha20Core.keystream(key, nonce, counter, length);
    }
}
