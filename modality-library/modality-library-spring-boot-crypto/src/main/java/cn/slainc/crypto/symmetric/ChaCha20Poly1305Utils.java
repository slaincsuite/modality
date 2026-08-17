package cn.slainc.crypto.symmetric;

import cn.slainc.crypto.CryptoException;
import cn.slainc.crypto.random.SecureRandomUtils;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.modes.ChaCha20Poly1305;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.KeyParameter;

public final class ChaCha20Poly1305Utils {

    public static final int KEY_LENGTH = 32;

    public static final int NONCE_LENGTH = 12;

    public static final int TAG_LENGTH = 16;

    private ChaCha20Poly1305Utils() {
    }

    public static byte[] generateKey() {
        return SecureRandomUtils.nextBytes(KEY_LENGTH);
    }

    public static byte[] generateNonce() {
        return SecureRandomUtils.nextBytes(NONCE_LENGTH);
    }

    public static byte[] encrypt(byte[] key, byte[] nonce, byte[] associatedData, byte[] plainText) {
        return process(true, key, nonce, associatedData, plainText);
    }

    public static byte[] decrypt(byte[] key, byte[] nonce, byte[] associatedData, byte[] cipherText) {
        if (cipherText == null || cipherText.length < TAG_LENGTH) {
            throw new CryptoException("ChaCha20-Poly1305 数据长度不足");
        }
        return process(false, key, nonce, associatedData, cipherText);
    }

    public static byte[] seal(byte[] key, byte[] plainText) {
        byte[] nonce = generateNonce();
        byte[] sealed = encrypt(key, nonce, null, plainText);
        byte[] output = new byte[nonce.length + sealed.length];
        System.arraycopy(nonce, 0, output, 0, nonce.length);
        System.arraycopy(sealed, 0, output, nonce.length, sealed.length);
        return output;
    }

    public static byte[] open(byte[] key, byte[] sealed) {
        if (sealed == null || sealed.length < NONCE_LENGTH + TAG_LENGTH) {
            throw new CryptoException("ChaCha20-Poly1305 数据长度不足");
        }
        byte[] nonce = new byte[NONCE_LENGTH];
        System.arraycopy(sealed, 0, nonce, 0, NONCE_LENGTH);
        byte[] cipherText = new byte[sealed.length - NONCE_LENGTH];
        System.arraycopy(sealed, NONCE_LENGTH, cipherText, 0, cipherText.length);
        return decrypt(key, nonce, null, cipherText);
    }

    private static byte[] process(boolean encrypt, byte[] key, byte[] nonce, byte[] associatedData, byte[] data) {
        if (key == null || key.length != KEY_LENGTH) {
            throw new CryptoException("ChaCha20-Poly1305 密钥长度必须为 " + KEY_LENGTH + " 字节");
        }
        if (nonce == null || nonce.length != NONCE_LENGTH) {
            throw new CryptoException("ChaCha20-Poly1305 随机数长度必须为 " + NONCE_LENGTH + " 字节");
        }
        if (data == null) {
            throw new CryptoException("数据不能为空");
        }
        ChaCha20Poly1305 cipher = new ChaCha20Poly1305();
        AEADParameters parameters = new AEADParameters(new KeyParameter(key), TAG_LENGTH * 8, nonce, associatedData);
        cipher.init(encrypt, parameters);
        byte[] output = new byte[cipher.getOutputSize(data.length)];
        int length = cipher.processBytes(data, 0, data.length, output, 0);
        try {
            cipher.doFinal(output, length);
        } catch (InvalidCipherTextException exception) {
            throw new CryptoException("无法使用 ChaCha20-Poly1305 " + (encrypt ? "加密" : "解密"), exception);
        }
        return output;
    }
}
