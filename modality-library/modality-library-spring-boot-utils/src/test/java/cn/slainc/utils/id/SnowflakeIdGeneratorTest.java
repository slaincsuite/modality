package cn.slainc.utils.id;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnowflakeIdGeneratorTest {

    @Test
    void generatesUniqueMonotonicIds() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1L, 1L);
        Set<Long> ids = new HashSet<>();
        long previous = -1L;
        for (int i = 0; i < 20000; i++) {
            long id = generator.nextId();
            assertTrue(ids.add(id), "生成了重复的 ID");
            assertTrue(id > previous, "ID 未保持单调递增");
            previous = id;
        }
        assertEquals(20000, ids.size());
    }

    @Test
    void encodesAndDecodesComponents() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(7L, 3L);
        long id = generator.nextId();
        assertEquals(7L, generator.workerIdOf(id));
        assertEquals(3L, generator.datacenterIdOf(id));
        assertTrue(generator.timestampOf(id) > 0L);
        assertTrue(generator.sequenceOf(id) >= 0L);
    }

    @Test
    void rejectsInvalidWorkerAndDatacenter() {
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(32L));
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(0L, 32L));
    }

    @Test
    void createsFromAssigner() {
        SnowflakeIdGenerator generator = SnowflakeIdGenerator.create(new WorkerIdAssigner() {
            @Override
            public long assignWorkerId() {
                return 5L;
            }

            @Override
            public long assignDatacenterId() {
                return 2L;
            }
        });
        assertEquals(5L, generator.workerId());
        assertEquals(2L, generator.datacenterId());
        assertTrue(generator.nextIdString().length() > 0);
    }
}
