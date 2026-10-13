package cn.slainc.workorder.number;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;


public class MemoryWorkOrderSequenceStore implements WorkOrderSequenceStore {

    private final Map<String, AtomicLong> sequences = new ConcurrentHashMap<>();

    @Override
    public long next(String key) {
        return sequences.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
    }
}
