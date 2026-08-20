package cn.slainc.excel.imports;

import cn.slainc.excel.read.ImportError;
import cn.slainc.excel.read.ReadOptions;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

public final class ImportSession<T> {

    private final String sessionId;
    private final Class<T> head;
    private final String fileName;
    private final Path sourceFile;
    private final List<T> previewRows;
    private final List<ImportError> previewErrors;
    private final ReadOptions options;
    private final Instant createdAt;
    private final Instant expiresAt;

    public ImportSession(String sessionId, Class<T> head, String fileName, Path sourceFile, List<T> previewRows,
                         List<ImportError> previewErrors, ReadOptions options, Instant createdAt, Instant expiresAt) {
        this.sessionId = sessionId;
        this.head = head;
        this.fileName = fileName;
        this.sourceFile = sourceFile;
        this.previewRows = List.copyOf(previewRows);
        this.previewErrors = List.copyOf(previewErrors);
        this.options = options;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String getSessionId() {
        return sessionId;
    }

    public Class<T> getHead() {
        return head;
    }

    public String getFileName() {
        return fileName;
    }

    public Path getSourceFile() {
        return sourceFile;
    }

    public List<T> getPreviewRows() {
        return previewRows;
    }

    public List<ImportError> getPreviewErrors() {
        return previewErrors;
    }

    public ReadOptions getOptions() {
        return options;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public int getPreviewCount() {
        return previewRows.size();
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
}
