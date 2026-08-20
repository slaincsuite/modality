package cn.slainc.excel.imports;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public class InMemoryImportLogStore implements ImportLogStore {

    public static final int DEFAULT_MAX_ENTRIES = 500;

    private final Deque<ImportLogEntry> entries = new ArrayDeque<>();
    private final int maxEntries;

    public InMemoryImportLogStore() {
        this(DEFAULT_MAX_ENTRIES);
    }

    public InMemoryImportLogStore(int maxEntries) {
        this.maxEntries = maxEntries <= 0 ? DEFAULT_MAX_ENTRIES : maxEntries;
    }

    public int getMaxEntries() {
        return maxEntries;
    }

    @Override
    public synchronized void save(ImportLogEntry entry) {
        if (entry == null) {
            return;
        }
        entries.addFirst(entry);
        while (entries.size() > maxEntries) {
            entries.removeLast();
        }
    }

    @Override
    public synchronized List<ImportLogEntry> list(int limit) {
        int size = limit <= 0 ? entries.size() : Math.min(limit, entries.size());
        return entries.stream().limit(size).toList();
    }

    @Override
    public synchronized int size() {
        return entries.size();
    }

    @Override
    public synchronized void clear() {
        entries.clear();
    }
}
