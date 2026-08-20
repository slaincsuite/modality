package cn.slainc.excel.imports;

import cn.slainc.excel.read.ImportResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class ImportLog {

    private static final int MESSAGE_LIMIT = 1000;

    private final ImportLogStore store;

    public ImportLog(ImportLogStore store) {
        this.store = store == null ? new InMemoryImportLogStore() : store;
    }

    public ImportLogStore getStore() {
        return store;
    }

    public ImportLogEntry record(String fileName, Class<?> head, ImportResult<?> result, long durationMillis) {
        int totalRows = result == null ? 0 : result.getTotalRows();
        int successCount = result == null ? 0 : result.getSuccessCount();
        int errorCount = result == null ? 0 : result.getErrorCount();
        boolean success = result != null && result.isSuccess();
        String message = success ? "导入成功" : result == null ? "导入失败" : shorten(result.getErrorMessage());
        ImportLogEntry entry = new ImportLogEntry(UUID.randomUUID().toString().replace("-", ""), fileName,
                head == null ? null : head.getName(), totalRows, successCount, errorCount, durationMillis, success,
                message, Instant.now());
        store.save(entry);
        return entry;
    }

    public List<ImportLogEntry> recent(int limit) {
        return store.list(limit);
    }

    public List<ImportLogEntry> all() {
        return store.list(0);
    }

    public int size() {
        return store.size();
    }

    public void clear() {
        store.clear();
    }

    private String shorten(String message) {
        if (message == null) {
            return "导入失败";
        }
        return message.length() <= MESSAGE_LIMIT ? message : message.substring(0, MESSAGE_LIMIT) + "...";
    }
}
