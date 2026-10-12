package cn.slainc.thirdauth.state;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryStateStoreTest {

    @Test
    void expiredStatesAreEvictedOnWrite() throws InterruptedException {
        MemoryStateStore store = new MemoryStateStore();
        store.put("expired-state", 1);
        Thread.sleep(1200);
        store.put("fresh-state", 60);
        assertThat(store.size()).as("过期状态应在写入时被清掉").isEqualTo(1);
        assertThat(store.consume("expired-state")).isFalse();
        assertThat(store.consume("fresh-state")).isTrue();
        assertThat(store.size()).isZero();
    }

    @Test
    void createdStatesAreConsumableOnce() {
        MemoryStateStore store = new MemoryStateStore();
        String state = store.create(60);
        assertThat(store.consume(state)).isTrue();
        assertThat(store.consume(state)).isFalse();
    }

    @Test
    void blankStateIsIgnored() {
        MemoryStateStore store = new MemoryStateStore();
        store.put("  ", 60);
        assertThat(store.size()).isZero();
        assertThat(store.consume(null)).isFalse();
    }
}
