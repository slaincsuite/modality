package cn.slainc.crypto.key;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cn.slainc.crypto.CryptoException;
import cn.slainc.crypto.symmetric.AesKeySize;
import cn.slainc.crypto.symmetric.WeakAlgorithmPolicy;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SecretKeyUtilsTest {

    @BeforeAll
    static void allowWeakAlgorithms() {
        WeakAlgorithmPolicy.allowWeakAlgorithms(true);
    }

    @AfterAll
    static void resetWeakAlgorithms() {
        WeakAlgorithmPolicy.allowWeakAlgorithms(false);
    }

    @Test
    void createsSymmetricKeys() {
        assertEquals(32, SecretKeyUtils.aesKey(AesKeySize.AES_256).getEncoded().length);
        assertEquals(16, SecretKeyUtils.sm4Key().getEncoded().length);
        assertEquals(8, SecretKeyUtils.desKey().getEncoded().length);
        assertEquals(24, SecretKeyUtils.tripleDesKey().getEncoded().length);
        assertEquals(32, SecretKeyUtils.hmacKey("HmacSHA256", 32).getEncoded().length);
        assertEquals("SM4", SecretKeyUtils.sm4Key().getAlgorithm());
    }

    @Test
    void roundTripsBase64AndHex() {
        SecretKey key = SecretKeyUtils.sm4Key();
        assertArrayEquals(key.getEncoded(), SecretKeyUtils.decodeBase64(SecretKeyUtils.encodeBase64(key), "SM4").getEncoded());
        assertArrayEquals(key.getEncoded(), SecretKeyUtils.decodeHex(SecretKeyUtils.encodeHex(key), "SM4").getEncoded());
        assertArrayEquals(key.getEncoded(), SecretKeyUtils.decode("base64:" + SecretKeyUtils.encodeBase64(key), "SM4").getEncoded());
        assertArrayEquals(key.getEncoded(), SecretKeyUtils.decode("hex:" + SecretKeyUtils.encodeHex(key), "SM4").getEncoded());
        assertArrayEquals(key.getEncoded(), SecretKeyUtils.decode(SecretKeyUtils.encodeBase64(key), "SM4").getEncoded());
        assertArrayEquals("modality".getBytes(java.nio.charset.StandardCharsets.UTF_8), SecretKeyUtils.fromPassword("modality", "SM4").getEncoded());
    }

    @Test
    void rejectsIllegalKeys() {
        assertThrows(CryptoException.class, () -> SecretKeyUtils.sm4Key(new byte[15]));
        assertThrows(CryptoException.class, () -> SecretKeyUtils.aesKey(new byte[20]));
        assertThrows(CryptoException.class, () -> SecretKeyUtils.desKey(new byte[7]));
        assertThrows(CryptoException.class, () -> SecretKeyUtils.tripleDesKey(new byte[9]));
        assertThrows(CryptoException.class, () -> SecretKeyUtils.decode(null, "SM4"));
    }
}
