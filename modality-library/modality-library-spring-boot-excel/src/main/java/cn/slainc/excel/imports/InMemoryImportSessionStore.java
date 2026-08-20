package cn.slainc.excel.imports;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryImportSessionStore implements ImportSessionStore {

    public static final Duration DEFAULT_TTL = Duration.ofMinutes(30);

    private final Map<String, ImportSession<?>> sessions = new ConcurrentHashMap<>();
    private final Path workDirectory;
    private final Duration ttl;

    public InMemoryImportSessionStore() {
        this(defaultDirectory(), DEFAULT_TTL);
    }

    public InMemoryImportSessionStore(Path workDirectory, Duration ttl) {
        this.workDirectory = workDirectory == null ? defaultDirectory() : workDirectory;
        this.ttl = ttl == null || ttl.isNegative() || ttl.isZero() ? DEFAULT_TTL : ttl;
        try {
            Files.createDirectories(this.workDirectory);
        } catch (IOException ex) {
            throw new IllegalStateException("创建导入会话目录失败: " + this.workDirectory, ex);
        }
    }

    public Duration getTtl() {
        return ttl;
    }

    public Path getWorkDirectory() {
        return workDirectory;
    }

    @Override
    public Path stage(String sessionId, Path source) {
        if (source == null || !Files.isRegularFile(source)) {
            throw new IllegalArgumentException("导入文件不存在: " + source);
        }
        Path target = workDirectory.resolve(sessionId + "-" + source.getFileName());
        try {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new IllegalStateException("暂存导入文件失败: " + source, ex);
        }
        return target;
    }

    @Override
    public void save(ImportSession<?> session) {
        if (session == null || session.getSessionId() == null) {
            throw new IllegalArgumentException("导入会话不能为空");
        }
        sessions.put(session.getSessionId(), session);
    }

    @Override
    public ImportSession<?> find(String sessionId) {
        return sessionId == null ? null : sessions.get(sessionId);
    }

    @Override
    public void remove(String sessionId) {
        ImportSession<?> session = sessionId == null ? null : sessions.remove(sessionId);
        if (session != null) {
            deleteQuietly(session.getSourceFile());
        }
    }

    @Override
    public List<ImportSession<?>> list() {
        return sessions.values().stream()
                .sorted(Comparator.comparing((ImportSession<?> session) -> session.getCreatedAt()).reversed())
                .toList();
    }

    @Override
    public int clearExpired() {
        Instant now = Instant.now();
        int removed = 0;
        for (ImportSession<?> session : sessions.values()) {
            if (session.getExpiresAt() != null && now.isAfter(session.getExpiresAt())) {
                remove(session.getSessionId());
                removed++;
            }
        }
        return removed;
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            path.toFile().deleteOnExit();
        }
    }

    private static Path defaultDirectory() {
        return Paths.get(System.getProperty("java.io.tmpdir"), "modality-excel-import");
    }
}
