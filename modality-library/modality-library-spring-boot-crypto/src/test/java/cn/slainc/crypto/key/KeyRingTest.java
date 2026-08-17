package cn.slainc.crypto.key;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.slainc.crypto.CryptoException;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

class KeyRingTest {

    @Test
    void createsRingWithGeneratedKey() {
        KeyRing ring = KeyRing.create("AES", 32);
        assertEquals("AES", ring.algorithm());
        assertEquals(32, ring.keyLength());
        assertEquals(KeyRing.DEFAULT_KEY_ID, ring.currentKeyId());
        assertEquals(32, ring.currentKeyBytes().length);
        assertEquals(1, ring.keyIds().size());
    }

    @Test
    void rotatesKeysAndKeepsHistory() {
        KeyRing ring = KeyRing.create("AES", 32, "v1");
        byte[] first = ring.currentKeyBytes();
        KeyRing rotated = ring.rotate("v2");
        assertEquals("v2", rotated.currentKeyId());
        assertEquals(2, rotated.keyIds().size());
        assertArrayEquals(first, rotated.key("v1").getEncoded());
        assertNotEquals(rotated.currentKeyId(), ring.currentKeyId());
        KeyRing random = ring.rotate();
        assertTrue(random.keyIds().contains(random.currentKeyId()));
        assertEquals(2, random.keyIds().size());
    }

    @Test
    void switchesCurrentAndRemovesKeys() {
        KeyRing ring = KeyRing.create("SM4", 16, "v1").rotate("v2");
        assertEquals("v2", ring.currentKeyId());
        assertEquals("v1", ring.withCurrent("v1").currentKeyId());
        assertEquals(1, ring.remove("v1").keyIds().size());
        assertEquals("v1", ring.remove("v2").currentKeyId());
        assertThrows(CryptoException.class, () -> ring.withCurrent("missing"));
        assertThrows(CryptoException.class, () -> ring.key("missing"));
        assertThrows(CryptoException.class, () -> KeyRing.of("v1", SecretKeyUtils.sm4Key()).remove("v1"));
    }

    @Test
    void buildsRingFromExistingKey() {
        SecretKey key = SecretKeyUtils.aesKey(cn.slainc.crypto.symmetric.AesKeySize.AES_128);
        KeyRing ring = KeyRing.of(key);
        assertSame(key, ring.currentKey());
        assertEquals(16, ring.keyLength());
        KeyRing added = ring.add("extra", key);
        assertEquals(2, added.keyIds().size());
        assertEquals("AES", added.add("extra", key).key("extra").getAlgorithm());
    }
}
