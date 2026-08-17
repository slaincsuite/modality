package cn.slainc.crypto.symmetric;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.slainc.crypto.CryptoException;
import cn.slainc.crypto.codec.HexUtils;
import cn.slainc.crypto.random.SecureRandomUtils;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

class AesUtilsTest {

    private static final byte[] SP800_KEY = HexUtils.decode("2b7e151628aed2a6abf7158809cf4f3c");

    private static final byte[] SP800_IV = HexUtils.decode("000102030405060708090a0b0c0d0e0f");

    private static final byte[] SP800_COUNTER = HexUtils.decode("f0f1f2f3f4f5f6f7f8f9fafbfcfdfeff");

    private static final byte[] SP800_PLAIN = HexUtils.decode("6bc1bee22e409f96e93d7e117393172a"
            + "ae2d8a571e03ac9c9eb76fac45af8e51"
            + "30c81c46a35ce411e5fbc1191a0a52ef"
            + "f69f2445df4f9b17ad2b417be66c3710");

    @Test
    void matchesSp80038aEcbVector() {
        assertEquals("3ad77bb40d7a3660a89ecaf32466ef97f5d3d58503b9699de785895a96fdbaaf"
                        + "43b1cd7f598ece23881b00e3ed0306887b0c785e27e8ad3f8223207104725dd4",
                HexUtils.encode(AesUtils.encrypt(CipherMode.ECB, Padding.NONE, SP800_KEY, null, SP800_PLAIN)));
    }

    @Test
    void matchesSp80038aCbcVector() {
        byte[] cipherText = AesUtils.encrypt(CipherMode.CBC, Padding.NONE, SP800_KEY, SP800_IV, SP800_PLAIN);
        assertEquals("7649abac8119b246cee98e9b12e9197d5086cb9b507219ee95db113a917678b2"
                + "73bed6b8e3c1743b7116e69e222295163ff1caa1681fac09120eca307586e1a7", HexUtils.encode(cipherText));
        assertArrayEquals(SP800_PLAIN, AesUtils.decrypt(CipherMode.CBC, Padding.NONE, SP800_KEY, SP800_IV, cipherText));
    }

    @Test
    void matchesSp80038aCtrVector() {
        byte[] cipherText = AesUtils.encrypt(CipherMode.CTR, Padding.NONE, SP800_KEY, SP800_COUNTER, SP800_PLAIN);
        assertEquals("874d6191b620e3261bef6864990db6ce9806f66b7970fdff8617187bb9fffdff"
                + "5ae4df3edbd5d35e5b4f09020db03eab1e031dda2fbe03d1792170a0f3009cee", HexUtils.encode(cipherText));
        assertArrayEquals(SP800_PLAIN, AesUtils.decrypt(CipherMode.CTR, Padding.NONE, SP800_KEY, SP800_COUNTER, cipherText));
    }

    @Test
    void matchesSp80038aAes256Vector() {
        byte[] key = HexUtils.decode("603deb1015ca71be2b73aef0857d77811f352c073b6108d72d9810a30914dff4");
        assertEquals("f3eed1bdb5d2a03c064b5a7e3db181f8",
                HexUtils.encode(AesUtils.encrypt(CipherMode.ECB, Padding.NONE, key, null, HexUtils.decode("6bc1bee22e409f96e93d7e117393172a"))));
    }

    @Test
    void matchesGcmKnownAnswerVectors() {
        byte[] key = new byte[16];
        byte[] iv = new byte[12];
        assertEquals("58e2fccefa7e3061367f1d57a4e7455a", HexUtils.encode(AesUtils.encrypt(CipherMode.GCM, Padding.NONE, key, iv, new byte[0])));
        assertEquals("0388dace60b6a392f328c2b971b2fe78ab6e47d42cec13bdf53a67b21257bddf",
                HexUtils.encode(AesUtils.encrypt(CipherMode.GCM, Padding.NONE, key, iv, new byte[16])));
    }

    @Test
    void roundTripsAllKeySizes() {
        byte[] data = "modality AES 测试".getBytes(StandardCharsets.UTF_8);
        for (AesKeySize keySize : AesKeySize.values()) {
            SecretKey key = AesUtils.generateKey(keySize);
            assertEquals(keySize.length(), key.getEncoded().length);
            assertEquals(keySize.bits(), keySize.length() * 8);
            byte[] iv = SecureRandomUtils.nextBytes(16);
            byte[] nonce = SecureRandomUtils.nextBytes(12);
            assertArrayEquals(data, AesUtils.decrypt(CipherMode.GCM, Padding.NONE, key.getEncoded(), nonce,
                    AesUtils.encrypt(CipherMode.GCM, Padding.NONE, key.getEncoded(), nonce, data)));
            assertArrayEquals(data, AesUtils.decrypt(CipherMode.CBC, Padding.PKCS7, key.getEncoded(), iv,
                    AesUtils.encrypt(CipherMode.CBC, Padding.PKCS7, key.getEncoded(), iv, data)));
            assertArrayEquals(data, AesUtils.decrypt(CipherMode.ECB, Padding.PKCS5, key.getEncoded(), null,
                    AesUtils.encrypt(CipherMode.ECB, Padding.PKCS5, key.getEncoded(), null, data)));
            byte[] counter = SecureRandomUtils.nextBytes(16);
            assertArrayEquals(data, AesUtils.decrypt(CipherMode.CTR, Padding.NONE, key.getEncoded(), counter,
                    AesUtils.encrypt(CipherMode.CTR, Padding.NONE, key.getEncoded(), counter, data)));
        }
    }

    @Test
    void supportsKeyObjectsAndHelpers() {
        SecretKey key = AesUtils.generateKey();
        byte[] iv = SecureRandomUtils.nextBytes(16);
        byte[] data = "key objects".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(data, AesUtils.decrypt(CipherMode.CBC, Padding.PKCS7, key, iv, AesUtils.encrypt(CipherMode.CBC, Padding.PKCS7, key, iv, data)));
        byte[] sealed = AesUtils.encryptGcm(key.getEncoded(), data);
        assertArrayEquals(data, AesUtils.decryptGcm(key.getEncoded(), sealed));
        assertEquals(12 + data.length + 16, sealed.length);
        assertArrayEquals(key.getEncoded(), AesUtils.key(key.getEncoded()).getEncoded());
    }

    @Test
    void detectsTamperedAndIllegalInput() {
        byte[] key = SecureRandomUtils.nextBytes(32);
        byte[] iv = SecureRandomUtils.nextBytes(12);
        byte[] sealed = AesUtils.encrypt(CipherMode.GCM, Padding.NONE, key, iv, "secret".getBytes(StandardCharsets.UTF_8));
        sealed[0] ^= 0x01;
        assertThrows(CryptoException.class, () -> AesUtils.decrypt(CipherMode.GCM, Padding.NONE, key, iv, sealed));
        assertThrows(CryptoException.class, () -> AesUtils.encrypt(CipherMode.ECB, Padding.NONE, new byte[7], null, new byte[16]));
        assertThrows(CryptoException.class, () -> AesUtils.encrypt(CipherMode.CTR, Padding.PKCS7, key, iv, new byte[16]));
        assertThrows(CryptoException.class, () -> AesUtils.encrypt(CipherMode.ECB, Padding.NONE, key, null, new byte[15]));
        assertThrows(CryptoException.class, () -> AesUtils.decryptGcm(key, new byte[8]));
        assertNotEquals(HexUtils.encode(AesUtils.encrypt(CipherMode.ECB, Padding.PKCS7, key, null, new byte[16])),
                HexUtils.encode(AesUtils.encrypt(CipherMode.ECB, Padding.PKCS7, key, null, new byte[17])));
        assertEquals("AES", AesUtils.generateKey().getAlgorithm());
    }
}
