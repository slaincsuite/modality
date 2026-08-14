package cn.slainc.crypto.hash;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.slainc.crypto.CryptoException;
import cn.slainc.crypto.codec.HexUtils;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class Pbkdf2UtilsTest {

    private static final char[] PASSWORD = "password".toCharArray();

    private static final byte[] SALT = "salt".getBytes(StandardCharsets.UTF_8);

    @Test
    void matchesRfc6070Sha1Vectors() {
        assertEquals("0c60c80f961f0e71f3a9b524af6012062fe037a6",
                HexUtils.encode(Pbkdf2Utils.derive(Pbkdf2Algorithm.PBKDF2_HMAC_SHA1, PASSWORD, SALT, 1, 20)));
        assertEquals("ea6c014dc72d6f8ccd1ed92ace1d41f0d8de8957",
                HexUtils.encode(Pbkdf2Utils.derive(Pbkdf2Algorithm.PBKDF2_HMAC_SHA1, PASSWORD, SALT, 2, 20)));
        assertEquals("4b007901b765489abead49d926f721d065a429c1",
                HexUtils.encode(Pbkdf2Utils.derive(Pbkdf2Algorithm.PBKDF2_HMAC_SHA1, PASSWORD, SALT, 4096, 20)));
        assertEquals("3d2eec4fe41c849b80c8d83662c0e44a8b291a964cf2f07038",
                HexUtils.encode(Pbkdf2Utils.derive(Pbkdf2Algorithm.PBKDF2_HMAC_SHA1,
                        "passwordPASSWORDpassword".toCharArray(), "saltSALTsaltSALTsaltSALTsaltSALTsalt".getBytes(StandardCharsets.UTF_8), 4096, 25)));
        assertEquals("56fa6aa75548099dcc37d7f03425e0c3",
                HexUtils.encode(Pbkdf2Utils.derive(Pbkdf2Algorithm.PBKDF2_HMAC_SHA1,
                        "pass\u0000word".toCharArray(), "sa\u0000lt".getBytes(StandardCharsets.UTF_8), 4096, 16)));
    }

    @Test
    void matchesSha256KnownVectors() {
        assertEquals("120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b",
                HexUtils.encode(Pbkdf2Utils.derive(Pbkdf2Algorithm.PBKDF2_HMAC_SHA256, PASSWORD, SALT, 1, 32)));
        assertEquals("c5e478d59288c841aa530db6845c4c8d962893a001ce4e11a4963873aa98134a",
                HexUtils.encode(Pbkdf2Utils.derive(Pbkdf2Algorithm.PBKDF2_HMAC_SHA256, PASSWORD, SALT, 4096, 32)));
        assertEquals(64, Pbkdf2Utils.derive(Pbkdf2Algorithm.PBKDF2_HMAC_SHA512, PASSWORD, SALT, 2, 64).length);
    }

    @Test
    void derivesFromBytePasswords() {
        byte[] expected = Pbkdf2Utils.derive(Pbkdf2Algorithm.PBKDF2_HMAC_SHA256, PASSWORD, SALT, 10, 32);
        assertArrayEquals(expected, Pbkdf2Utils.derive(Pbkdf2Algorithm.PBKDF2_HMAC_SHA256, "password".getBytes(StandardCharsets.UTF_8), SALT, 10, 32));
        assertEquals(HexUtils.encode(expected), Pbkdf2Utils.deriveHex(Pbkdf2Algorithm.PBKDF2_HMAC_SHA256, PASSWORD, SALT, 10, 32));
    }

    @Test
    void hashesAndVerifiesEncodedValues() {
        String encoded = Pbkdf2Utils.hashEncoded(Pbkdf2Algorithm.PBKDF2_HMAC_SHA256, "国密口令".toCharArray(), 10000, 32);
        assertTrue(encoded.startsWith("pbkdf2-sha256$10000$"));
        assertTrue(Pbkdf2Utils.verify("国密口令", encoded));
        assertFalse(Pbkdf2Utils.verify("错误口令", encoded));
        assertFalse(Pbkdf2Utils.verify((String) null, encoded));
        assertFalse(Pbkdf2Utils.verify("国密口令", null));
        assertTrue(Pbkdf2Utils.verify("modality".toCharArray(), Pbkdf2Utils.hashEncoded(Pbkdf2Algorithm.PBKDF2_HMAC_SHA1, "modality".toCharArray(), 1000, 20)));
        assertEquals(Pbkdf2Algorithm.PBKDF2_HMAC_SHA512, Pbkdf2Algorithm.fromLabel("PBKDF2-SHA512"));
        assertThrows(IllegalArgumentException.class, () -> Pbkdf2Algorithm.fromLabel("pbkdf2-md5"));
    }

    @Test
    void rejectsIllegalArguments() {
        assertThrows(CryptoException.class, () -> Pbkdf2Utils.derive(Pbkdf2Algorithm.PBKDF2_HMAC_SHA256, PASSWORD, new byte[0], 1, 32));
        assertThrows(CryptoException.class, () -> Pbkdf2Utils.derive(Pbkdf2Algorithm.PBKDF2_HMAC_SHA256, PASSWORD, SALT, 0, 32));
        assertThrows(CryptoException.class, () -> Pbkdf2Utils.derive(Pbkdf2Algorithm.PBKDF2_HMAC_SHA256, (char[]) null, SALT, 1, 32));
        assertThrows(CryptoException.class, () -> Pbkdf2Utils.verify(PASSWORD, "pbkdf2-sha256$1$salt"));
        assertEquals(210000, Pbkdf2Utils.DEFAULT_ITERATIONS);
        assertEquals(16, Pbkdf2Utils.DEFAULT_SALT_LENGTH);
        assertEquals(32, Pbkdf2Utils.DEFAULT_KEY_LENGTH);
    }
}
