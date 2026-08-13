package cn.slainc.utils.id;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SegmentIdGeneratorTest {

    @Test
    void allocatesSegmentsLazily() {
        AtomicLong allocatorCalls = new AtomicLong(0L);
        SegmentAllocator allocator = (key, step) -> allocatorCalls.incrementAndGet() * step;
        SegmentIdGenerator generator = new SegmentIdGenerator("order", 100, allocator);

        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 250; i++) {
            ids.add(generator.nextId());
        }
        assertEquals(1L, ids.get(0));
        assertEquals(100L, ids.get(99));
        assertEquals(101L, ids.get(100));
        assertEquals(250L, ids.get(249));
        assertEquals(3L, allocatorCalls.get());
        assertEquals(300L, generator.currentMax());
    }

    @Test
    void rejectsInvalidArguments() {
        SegmentAllocator allocator = (key, step) -> 100L;
        assertThrows(IllegalArgumentException.class, () -> new SegmentIdGenerator("k", 0, allocator));
        assertThrows(IllegalArgumentException.class, () -> new SegmentIdGenerator("k", 10, null));
    }

    @Test
    void exposesConfiguration() {
        SegmentIdGenerator generator = new SegmentIdGenerator("user", 50, (key, step) -> 50L);
        assertEquals("user", generator.key());
        assertEquals(50, generator.step());
        assertTrue(generator.nextIdString().length() > 0);
    }
}
