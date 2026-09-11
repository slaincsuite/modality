package cn.slainc.captcha.store;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryCaptchaStoreTest {

    private final MemoryCaptchaStore store = new MemoryCaptchaStore();

    @Test
    void putThenGetReturnsValue() {
        store.put("k", "v", Duration.ofSeconds(30));
        assertEquals("v", store.get("k"));
    }

    @Test
    void getMissingKeyReturnsNull() {
        assertNull(store.get("missing"));
    }

    @Test
    void removeDeletesEntry() {
        store.put("k", "v", Duration.ofSeconds(30));
        store.remove("k");
        assertNull(store.get("k"));
    }

    @Test
    void expiredEntryIsInvisible() throws InterruptedException {
        store.put("k", "v", Duration.ofMillis(30));
        Thread.sleep(60);
        assertNull(store.get("k"));
        assertFalse(store.isLocked("k"));
    }

    @Test
    void failCountStartsAtZero() {
        assertEquals(0L, store.failCount("none"));
    }

    @Test
    void incrementFailAccumulates() {
        assertEquals(1L, store.incrementFail("f", Duration.ofSeconds(30)));
        assertEquals(2L, store.incrementFail("f", Duration.ofSeconds(30)));
        assertEquals(3L, store.incrementFail("f", Duration.ofSeconds(30)));
        assertEquals(3L, store.failCount("f"));
    }

    @Test
    void incrementFailRespectsTtl() throws InterruptedException {
        store.incrementFail("f", Duration.ofMillis(40));
        Thread.sleep(80);
        assertEquals(0L, store.failCount("f"));
        assertEquals(1L, store.incrementFail("f", Duration.ofSeconds(30)));
    }

    @Test
    void lockMakesKeyVisible() {
        assertFalse(store.isLocked("lock"));
        store.lock("lock", Duration.ofSeconds(30));
        assertTrue(store.isLocked("lock"));
    }

    @Test
    void expiredLockIsReleased() throws InterruptedException {
        store.lock("lock", Duration.ofMillis(40));
        Thread.sleep(80);
        assertFalse(store.isLocked("lock"));
    }
}
