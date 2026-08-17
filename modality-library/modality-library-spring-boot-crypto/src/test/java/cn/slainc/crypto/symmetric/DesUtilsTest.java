package cn.slainc.crypto.symmetric;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cn.slainc.crypto.CryptoException;
import cn.slainc.crypto.codec.HexUtils;
import cn.slainc.crypto.random.SecureRandomUtils;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DesUtilsTest {

    @Test
    void matchesClassicDesVector() {
        byte[] key = HexUtils.decode("133457799BBCDFF1");
        byte[] plain = HexUtils.decode("0123456789ABCDEF");
        byte[] cipherText = DesUtils.encryptDes(CipherMode.ECB, Padding.NONE, key, null, plain);
        assertEquals("85e813540f0ab405", HexUtils.encode(cipherText));
        assertArrayEquals(plain, DesUtils.decryptDes(CipherMode.ECB, Padding.NONE, key, null, cipherText));
    }

    @Test
    void matchesTripleDesCbcVector() {
        byte[] key = HexUtils.decode("0123456789abcdeffedcba98765432100123456789abcdef");
        byte[] iv = HexUtils.decode("0001020304050607");
        byte[] data = "modality".getBytes(StandardCharsets.UTF_8);
        byte[] cipherText = DesUtils.encryptTripleDes(CipherMode.CBC, Padding.PKCS5, key, iv, data);
        assertEquals("f7bef1979010badb0d68a543354136ab", HexUtils.encode(cipherText));
        assertArrayEquals(data, DesUtils.decryptTripleDes(CipherMode.CBC, Padding.PKCS5, key, iv, cipherText));
    }

    @Test
    void roundTripsBothAlgorithms() {
        byte[] data = "国密之外的对称算法".getBytes(StandardCharsets.UTF_8);
        byte[] desKey = DesUtils.generateKey().getEncoded();
        byte[] desIv = SecureRandomUtils.nextBytes(8);
        assertEquals(8, desKey.length);
        assertArrayEquals(data, DesUtils.decryptDes(CipherMode.CBC, Padding.PKCS7, desKey, desIv,
                DesUtils.encryptDes(CipherMode.CBC, Padding.PKCS7, desKey, desIv, data)));
        assertArrayEquals(data, DesUtils.decryptDes(CipherMode.ECB, Padding.PKCS5, desKey, null,
                DesUtils.encryptDes(CipherMode.ECB, Padding.PKCS5, desKey, null, data)));
        byte[] tripleKey = DesUtils.generateTripleDesKey().getEncoded();
        assertEquals(24, tripleKey.length);
        byte[] tripleIv = SecureRandomUtils.nextBytes(8);
        assertArrayEquals(data, DesUtils.decryptTripleDes(CipherMode.CBC, Padding.PKCS7, tripleKey, tripleIv,
                DesUtils.encryptTripleDes(CipherMode.CBC, Padding.PKCS7, tripleKey, tripleIv, data)));
        assertArrayEquals(data, DesUtils.decryptTripleDes(CipherMode.CTR, Padding.NONE, tripleKey, tripleIv,
                DesUtils.encryptTripleDes(CipherMode.CTR, Padding.NONE, tripleKey, tripleIv, data)));
        assertArrayEquals(tripleKey, DesUtils.tripleKey(tripleKey).getEncoded());
        assertArrayEquals(desKey, DesUtils.key(desKey).getEncoded());
    }

    @Test
    void rejectsIllegalKeys() {
        assertThrows(CryptoException.class, () -> DesUtils.key(new byte[7]));
        assertThrows(CryptoException.class, () -> DesUtils.tripleKey(new byte[8]));
        assertThrows(CryptoException.class, () -> DesUtils.encryptDes(CipherMode.ECB, Padding.NONE, new byte[16], null, new byte[8]));
        assertThrows(CryptoException.class, () -> DesUtils.encryptTripleDes(CipherMode.CBC, Padding.PKCS7, new byte[24], new byte[16],
                "data".getBytes(StandardCharsets.UTF_8)));
    }
}
