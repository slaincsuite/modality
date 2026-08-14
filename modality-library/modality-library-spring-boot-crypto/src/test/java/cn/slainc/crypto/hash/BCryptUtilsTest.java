package cn.slainc.crypto.hash;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.slainc.crypto.CryptoException;
import org.junit.jupiter.api.Test;

class BCryptUtilsTest {

    @Test
    void verifiesPublishedBcryptHashes() {
        assertTrue(BCryptUtils.verify("", "$2a$06$DCq7YPn5Rq63x1Lad4cll.TV4S6ytwfsfvkgY8jIucDrjc8deX1s."));
        assertTrue(BCryptUtils.verify("a", "$2a$06$m0CrhHm10qJ3lXRY.5zDGO3rS2KdeeWLuGmsfGlMfOxih58VYVfxe"));
        assertTrue(BCryptUtils.verify("abc", "$2a$06$If6bvum7DFjUnE9p2uDeDu0YHzrHM6tf.iqN8.yx.jNN1ILEf7h0i"));
        assertFalse(BCryptUtils.verify("abcd", "$2a$06$DCq7YPn5Rq63x1Lad4cll.TV4S6ytwfsfvkgY8jIucDrjc8deX1s."));
        assertFalse(BCryptUtils.verify("", "not-a-hash"));
        assertFalse(BCryptUtils.verify(null, "$2a$06$DCq7YPn5Rq63x1Lad4cll.TV4S6ytwfsfvkgY8jIucDrjc8deX1s."));
        assertFalse(BCryptUtils.verify("", null));
    }

    @Test
    void hashesAndVerifiesPasswords() {
        String hash = BCryptUtils.hash("modality-secret", 6);
        assertTrue(hash.startsWith("$2a$06$"));
        assertEquals(60, hash.length());
        assertTrue(BCryptUtils.verify("modality-secret", hash));
        assertFalse(BCryptUtils.verify("modality-secret ", hash));
        assertTrue(BCryptUtils.verify("国密口令", BCryptUtils.hash("国密口令", 4)));
        assertFalse(BCryptUtils.verify("国密口令", BCryptUtils.hash("国密口令2", 4)));
        assertEquals(BCryptUtils.DEFAULT_COST, BCryptUtils.cost(BCryptUtils.hash("modality")));
    }

    @Test
    void reportsCostAndRehashNeeds() {
        String hash = BCryptUtils.hash("modality", 5);
        assertEquals(5, BCryptUtils.cost(hash));
        assertTrue(BCryptUtils.needsRehash(hash, 10));
        assertFalse(BCryptUtils.needsRehash(hash, 5));
        assertTrue(BCryptUtils.needsRehash(null, 10));
        assertTrue(BCryptUtils.needsRehash("short", 10));
        assertThrows(CryptoException.class, () -> BCryptUtils.cost("bad"));
        assertThrows(CryptoException.class, () -> BCryptUtils.hash(null, 10));
        assertThrows(CryptoException.class, () -> BCryptUtils.hash("modality", 3));
        assertThrows(CryptoException.class, () -> BCryptUtils.hash("modality", 32));
        assertEquals(4, BCryptUtils.MIN_COST);
        assertEquals(31, BCryptUtils.MAX_COST);
        assertEquals(16, BCryptUtils.SALT_LENGTH);
        assertEquals("2a", BCryptUtils.VERSION);
    }
}
