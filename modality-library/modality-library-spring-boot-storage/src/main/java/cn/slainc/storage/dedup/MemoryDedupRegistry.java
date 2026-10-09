package cn.slainc.storage.dedup;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MemoryDedupRegistry implements DedupRegistry {

    private final Map<String, String> logicalKeys = new ConcurrentHashMap<>();

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    @Override
    public Entry find(String id) {
        return id == null ? null : entries.get(id);
    }

    @Override
    public String lookup(String logicalId) {
        return logicalId == null ? null : logicalKeys.get(logicalId);
    }

    @Override
    public void bind(String logicalId, String id) {
        if (logicalId != null && id != null) {
            logicalKeys.put(logicalId, id);
        }
    }

    @Override
    public String unbind(String logicalId) {
        return logicalId == null ? null : logicalKeys.remove(logicalId);
    }

    @Override
    public Entry register(String id, String key) {
        Entry candidate = new Entry(id, key, 1);
        Entry previous = entries.putIfAbsent(id, candidate);
        return previous == null ? candidate : previous;
    }

    @Override
    public Entry retain(String id) {
        Entry[] result = new Entry[1];
        entries.computeIfPresent(id, (name, entry) -> {
            result[0] = new Entry(name, entry.key(), entry.references() + 1);
            return result[0];
        });
        return result[0];
    }

    @Override
    public Entry release(String id) {
        Entry[] result = new Entry[1];
        entries.compute(id, (name, entry) -> {
            if (entry == null) {
                return null;
            }
            int references = entry.references() - 1;
            if (references <= 0) {
                result[0] = new Entry(name, entry.key(), 0);
                return null;
            }
            result[0] = new Entry(name, entry.key(), references);
            return result[0];
        });
        return result[0];
    }

    @Override
    public void remove(String id) {
        if (id != null) {
            entries.remove(id);
        }
    }
}
