package cn.slainc.excel.imports;

import cn.slainc.excel.ExcelUtils;
import cn.slainc.excel.read.ImportError;
import cn.slainc.excel.read.ImportPreview;
import cn.slainc.excel.read.ImportResult;
import cn.slainc.excel.read.ReadOptions;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class TwoPhaseImporter {

    public static final int DEFAULT_PREVIEW_ROWS = 20;

    private final ImportSessionStore store;
    private final Validator validator;
    private final ImportLog importLog;

    public TwoPhaseImporter() {
        this(new InMemoryImportSessionStore(), null, null);
    }

    public TwoPhaseImporter(ImportSessionStore store, Validator validator, ImportLog importLog) {
        this.store = store == null ? new InMemoryImportSessionStore() : store;
        this.validator = validator;
        this.importLog = importLog;
    }

    public ImportSessionStore getStore() {
        return store;
    }

    public <T> ImportSession<T> preview(Path file, Class<T> head, int maxRows, ReadOptions options) {
        if (head == null) {
            throw new IllegalArgumentException("导入行类型不能为空");
        }
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        Path staged = store.stage(sessionId, file);
        int limit = maxRows <= 0 ? DEFAULT_PREVIEW_ROWS : maxRows;
        List<T> rows = ImportPreview.preview(staged, head, limit);
        List<ImportError> errors = validate(rows);
        Instant now = Instant.now();
        java.time.Duration ttl = store instanceof InMemoryImportSessionStore memoryStore
                ? memoryStore.getTtl()
                : InMemoryImportSessionStore.DEFAULT_TTL;
        ImportSession<T> session = new ImportSession<>(sessionId, head, file.getFileName().toString(), staged, rows,
                errors, options == null ? ReadOptions.defaults() : options, now, now.plus(ttl));
        store.save(session);
        return session;
    }

    public <T> ImportResult<T> confirm(String sessionId) {
        return confirm(sessionId, null);
    }

    public <T> ImportResult<T> confirm(String sessionId, UniqueKeyDeduplicator<T> deduplicator) {
        ImportSession<T> session = require(sessionId);
        long startedAt = System.currentTimeMillis();
        ImportResult<T> result = ExcelUtils.read(session.getSourceFile(), session.getHead(), session.getOptions(),
                validator);
        if (deduplicator != null) {
            result = deduplicator.filter(result);
        }
        if (importLog != null) {
            importLog.record(session.getFileName(), session.getHead(), result,
                    System.currentTimeMillis() - startedAt);
        }
        store.remove(sessionId);
        return result;
    }

    public ImportSession<?> session(String sessionId) {
        return require(sessionId);
    }

    public List<ImportSession<?>> sessions() {
        return store.list();
    }

    public void discard(String sessionId) {
        store.remove(sessionId);
    }

    public int clearExpired() {
        return store.clearExpired();
    }

    @SuppressWarnings("unchecked")
    private <T> ImportSession<T> require(String sessionId) {
        ImportSession<?> session = store.find(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("导入会话不存在或已过期: " + sessionId);
        }
        if (session.isExpired()) {
            store.remove(sessionId);
            throw new IllegalArgumentException("导入会话已过期: " + sessionId);
        }
        return (ImportSession<T>) session;
    }

    private <T> List<ImportError> validate(List<T> rows) {
        List<ImportError> errors = new ArrayList<>();
        if (validator == null || rows == null) {
            return errors;
        }
        int rowIndex = 1;
        for (T row : rows) {
            rowIndex++;
            Set<ConstraintViolation<T>> violations = validator.validate(row);
            for (ConstraintViolation<T> violation : violations) {
                errors.add(ImportError.of(rowIndex, violation.getPropertyPath().toString(),
                        violation.getInvalidValue(), violation.getMessage()));
            }
        }
        return errors;
    }
}
