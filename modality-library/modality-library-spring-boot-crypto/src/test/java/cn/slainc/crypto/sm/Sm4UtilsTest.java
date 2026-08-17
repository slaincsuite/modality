package cn.slainc.crypto.sm;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.slainc.crypto.CryptoException;
import cn.slainc.crypto.codec.Base64Utils;
import cn.slainc.crypto.codec.HexUtils;
import cn.slainc.crypto.random.SecureRandomUtils;
import cn.slainc.crypto.symmetric.CipherMode;
import cn.slainc.crypto.symmetric.Padding;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

class Sm4UtilsTest {

    private static final byte[] GB_KEY = HexUtils.decode("0123456789abcdeffedcba9876543210");

    @Test
    void matchesGbT32907Vector() {
        byte[] cipherText = Sm4Utils.encrypt(CipherMode.ECB, Padding.NONE, GB_KEY, null, GB_KEY);
        assertEquals("681edf34d206965e86b3e94f536e4246", HexUtils.encode(cipherText));
        assertArrayEquals(GB_KEY, Sm4Utils.decrypt(CipherMode.ECB, Padding.NONE, GB_KEY, null, cipherText));
    }

    @Test
    void matchesGbT32907MillionRoundsVector() {
        byte[] block = GB_KEY.clone();
        for (int i = 0; i < 1000000; i++) {
            block = Sm4Utils.encrypt(CipherMode.ECB, Padding.NONE, GB_KEY, null, block);
        }
        assertEquals("595298c7c6fd271f0402f804c33d3f66", HexUtils.encode(block));
    }

    @Test
    void roundTripsEveryMode() {
        byte[] key = SecureRandomUtils.nextBytes(Sm4Utils.KEY_LENGTH);
        byte[] iv = SecureRandomUtils.nextBytes(Sm4Utils.BLOCK_LENGTH);
        byte[] data = "modality 国密 SM4 测试数据".getBytes(StandardCharsets.UTF_8);
        byte[] aligned = java.util.Arrays.copyOf(data, ((data.length + 15) / 16) * 16);
        for (Padding padding : Padding.values()) {
            byte[] payload = padding.isPadded() ? data : aligned;
            byte[] ecb = Sm4Utils.encrypt(CipherMode.ECB, padding, key, null, payload);
            assertArrayEquals(payload, Sm4Utils.decrypt(CipherMode.ECB, padding, key, null, ecb));
        }
        byte[] cbc = Sm4Utils.encryptCbc(key, iv, data);
        assertArrayEquals(data, Sm4Utils.decryptCbc(key, iv, cbc));
        byte[] ctr = Sm4Utils.encryptCtr(key, iv, data);
        assertArrayEquals(data, Sm4Utils.decryptCtr(key, iv, ctr));
        byte[] gcm = Sm4Utils.encryptGcm(key, iv, data);
        assertArrayEquals(data, Sm4Utils.decryptGcm(key, iv, gcm));
        assertEquals(data.length, ctr.length);
        assertEquals(data.length + 16, gcm.length);
    }

    @Test
    void treatsPkcs5AndPkcs7Identically() {
        byte[] key = SecureRandomUtils.nextBytes(16);
        byte[] data = "padding".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(Sm4Utils.encrypt(CipherMode.ECB, Padding.PKCS5, key, null, data),
                Sm4Utils.encrypt(CipherMode.ECB, Padding.PKCS7, key, null, data));
    }

    @Test
    void padsAndUnpadsAcrossBlockBoundaries() {
        byte[] key = SecureRandomUtils.nextBytes(16);
        for (int length : new int[]{0, 1, 15, 16, 17, 31, 32, 33, 255}) {
            byte[] data = new byte[length];
            byte[] cipherText = Sm4Utils.encrypt(CipherMode.CBC, Padding.PKCS7, key, new byte[16], data);
            assertEquals(0, cipherText.length % 16);
            assertTrue(cipherText.length > length);
            assertArrayEquals(data, Sm4Utils.decrypt(CipherMode.CBC, Padding.PKCS7, key, new byte[16], cipherText));
        }
    }

    @Test
    void detectsTamperedGcmData() {
        byte[] key = SecureRandomUtils.nextBytes(16);
        byte[] iv = SecureRandomUtils.nextBytes(12);
        byte[] cipherText = Sm4Utils.encryptGcm(key, iv, "secret".getBytes(StandardCharsets.UTF_8));
        cipherText[0] ^= 0x01;
        assertThrows(CryptoException.class, () -> Sm4Utils.decryptGcm(key, iv, cipherText));
        assertThrows(CryptoException.class, () -> Sm4Utils.decryptGcm(SecureRandomUtils.nextBytes(16), iv, cipherText));
    }

    @Test
    void rejectsIllegalArguments() {
        byte[] key = SecureRandomUtils.nextBytes(16);
        assertThrows(CryptoException.class, () -> Sm4Utils.encrypt(CipherMode.ECB, Padding.NONE, new byte[8], null, new byte[16]));
        assertThrows(CryptoException.class, () -> Sm4Utils.encrypt(CipherMode.CTR, Padding.PKCS7, key, new byte[16], new byte[16]));
        assertThrows(CryptoException.class, () -> Sm4Utils.encrypt(CipherMode.ECB, Padding.NONE, key, null, new byte[15]));
        assertThrows(CryptoException.class, () -> Sm4Utils.encrypt(CipherMode.CBC, Padding.NONE, key, new byte[8], new byte[16]));
        assertThrows(CryptoException.class, () -> Sm4Utils.decryptGcm(key, new byte[8]));
    }

    @Test
    void supportsKeyAndTextHelpers() {
        SecretKey secretKey = Sm4Utils.generateKey();
        assertEquals(Sm4Utils.KEY_LENGTH, secretKey.getEncoded().length);
        assertEquals("SM4", secretKey.getAlgorithm());
        byte[] data = "hello".getBytes(StandardCharsets.UTF_8);
        byte[] iv = SecureRandomUtils.nextBytes(16);
        String base64 = Sm4Utils.encryptBase64(CipherMode.CBC, Padding.PKCS7, secretKey.getEncoded(), iv, data);
        assertArrayEquals(data, Sm4Utils.decryptBase64(CipherMode.CBC, Padding.PKCS7, secretKey.getEncoded(), iv, base64));
        String hex = Sm4Utils.encryptHex(CipherMode.ECB, Padding.PKCS7, secretKey.getEncoded(), null, data);
        assertArrayEquals(data, Sm4Utils.decryptHex(CipherMode.ECB, Padding.PKCS7, secretKey.getEncoded(), null, hex));
        byte[] sealed = Sm4Utils.encryptGcm(secretKey.getEncoded(), data);
        assertArrayEquals(data, Sm4Utils.decryptGcm(secretKey.getEncoded(), sealed));
        assertEquals(data.length + Sm4Utils.GCM_IV_LENGTH + 16, sealed.length);
        assertNotEquals(base64, Base64Utils.encode(Sm4Utils.encrypt(CipherMode.ECB, Padding.PKCS7, secretKey.getEncoded(), null, data)));
    }
}
