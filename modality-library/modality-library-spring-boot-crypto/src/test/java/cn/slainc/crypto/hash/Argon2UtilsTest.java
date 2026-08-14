package cn.slainc.crypto.hash;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.slainc.crypto.CryptoException;
import cn.slainc.crypto.codec.HexUtils;
import org.junit.jupiter.api.Test;

class Argon2UtilsTest {

    private static final byte[] PASSWORD = new byte[32];

    private static final byte[] SALT = new byte[16];

    private static final byte[] SECRET = new byte[8];

    private static final byte[] ADDITIONAL = new byte[12];

    static {
        java.util.Arrays.fill(PASSWORD, (byte) 0x01);
        java.util.Arrays.fill(SALT, (byte) 0x02);
        java.util.Arrays.fill(SECRET, (byte) 0x03);
        java.util.Arrays.fill(ADDITIONAL, (byte) 0x04);
    }

    private static Argon2Params vectorParams() {
        return Argon2Params.of(32, 3, 4, 32).withSecret(SECRET).withAdditional(ADDITIONAL);
    }

    @Test
    void matchesRfc9106Argon2dVector() {
        byte[] hash = Argon2Utils.hash(Argon2Variant.ARGON2D, vectorParams(), PASSWORD, SALT);
        assertEquals("512b391b6f1162975371d30919734294f868e3be3984f3c1a13a4db9fabe4acb", HexUtils.encode(hash));
    }

    @Test
    void matchesRfc9106Argon2iVector() {
        byte[] hash = Argon2Utils.hash(Argon2Variant.ARGON2I, vectorParams(), PASSWORD, SALT);
        assertEquals("c814d9d1dc7f37aa13f0d77f2494bda1c8de6b016dd388d29952a4c4672b6ce8", HexUtils.encode(hash));
    }

    @Test
    void matchesRfc9106Argon2idVector() {
        byte[] hash = Argon2Utils.hash(Argon2Variant.ARGON2ID, vectorParams(), PASSWORD, SALT);
        assertEquals("0d640df58d78766c08c037a34a8b53c9d01ef0452d75b65eb52520e96b01e659", HexUtils.encode(hash));
    }

    @Test
    void hashesAndVerifiesEncodedValues() {
        Argon2Params params = Argon2Params.of(1024, 2, 1, 32);
        String encoded = Argon2Utils.hashEncoded(Argon2Variant.ARGON2ID, params, "国密口令".toCharArray());
        assertTrue(encoded.startsWith("$argon2id$v=19$m=1024,t=2,p=1$"));
        assertTrue(Argon2Utils.isArgon2(encoded));
        assertTrue(Argon2Utils.verify("国密口令", encoded));
        assertTrue(Argon2Utils.verify("国密口令".toCharArray(), encoded));
        assertFalse(Argon2Utils.verify("错误口令", encoded));
        assertFalse(Argon2Utils.verify((String) null, encoded));
        assertEquals("argon2d", Argon2Variant.ARGON2D.label());
        assertEquals("argon2i", Argon2Variant.ARGON2I.label());
        assertEquals(Argon2Variant.ARGON2D, Argon2Variant.fromLabel("ARGON2D"));
        assertThrows(IllegalArgumentException.class, () -> Argon2Variant.fromLabel("argon2x"));
        assertEquals(Argon2Variant.ARGON2ID, Argon2Variant.fromLabel("argon2id"));
        assertTrue(Argon2Variant.ARGON2ID.type() > Argon2Variant.ARGON2I.type());
    }

    @Test
    void appliesDefaultsAndRejectsIllegalInput() {
        Argon2Params defaults = Argon2Params.defaults();
        assertEquals(65536, defaults.memoryKb());
        assertEquals(3, defaults.iterations());
        assertEquals(1, defaults.parallelism());
        assertEquals(32, defaults.hashLength());
        assertEquals(64, Argon2Params.defaults().withHashLength(64).hashLength());
        assertEquals(4, defaults.withParallelism(4).parallelism());
        assertEquals(2, defaults.withIterations(2).iterations());
        assertEquals(1024, defaults.withMemoryKb(1024).memoryKb());
        assertThrows(CryptoException.class, () -> Argon2Utils.hash(Argon2Variant.ARGON2ID, defaults, new byte[0], SALT));
        assertThrows(CryptoException.class, () -> Argon2Utils.hash(Argon2Variant.ARGON2ID, defaults, PASSWORD, new byte[0]));
        assertThrows(CryptoException.class, () -> Argon2Utils.verify("password", "not-argon2"));
        assertThrows(CryptoException.class, () -> Argon2Utils.verify("password", "$argon2id$v=19$m=1024$salt$hash"));
        assertThrows(CryptoException.class, () -> Argon2Utils.verify("password", "$argon2id$v=19$m=1024,t=2,x=1$c2FsdA$aGFzaA"));
        assertFalse(Argon2Utils.verify((String) null, (String) null));
        assertFalse(Argon2Utils.verify("password", null));
        assertFalse(Argon2Utils.verify((char[]) null, "$argon2id$v=19$m=1024,t=2,p=1$c2FsdA$aGFzaA"));
        assertEquals(16, Argon2Utils.DEFAULT_SALT_LENGTH);
    }
}
