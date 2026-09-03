package cn.slainc.logger.archive;

import cn.slainc.logger.autoconfigure.LoggerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class LogCleanupService {

    private static final Logger log = LoggerFactory.getLogger(LogCleanupService.class);

    private final LoggerProperties.Archive properties;

    private final Path hotDirectory;

    private final Path archiveDirectory;

    private final String activeFileName;

    public LogCleanupService(LoggerProperties.Archive properties, Path hotDirectory, Path archiveDirectory,
                             String activeFileName) {
        this.properties = properties;
        this.hotDirectory = hotDirectory;
        this.archiveDirectory = archiveDirectory;
        this.activeFileName = activeFileName;
    }

    public List<Path> cleanup() {
        return cleanup(Instant.now());
    }

    public List<Path> cleanup(Instant now) {
        if (!properties.isEnabled()) {
            return List.of();
        }
        List<Path> deleted = new ArrayList<>();
        deleteExpired(hotDirectory, now.minus(properties.getRetentionDays(), ChronoUnit.DAYS), deleted, false);
        deleteExpired(archiveDirectory, now.minus(properties.getArchiveRetentionDays(), ChronoUnit.DAYS), deleted, true);
        return deleted;
    }

    public List<Path> deleteExpired(Path directory, Instant threshold, boolean recursive) {
        List<Path> deleted = new ArrayList<>();
        deleteExpired(directory, threshold, deleted, recursive);
        return deleted;
    }

    private void deleteExpired(Path directory, Instant threshold, List<Path> deleted, boolean recursive) {
        if (directory == null || !Files.isDirectory(directory)) {
            return;
        }
        List<Path> candidates;
        try (Stream<Path> stream = Files.walk(directory, recursive ? Integer.MAX_VALUE : 1)) {
            candidates = stream.filter(Files::isRegularFile).sorted().toList();
        } catch (IOException ex) {
            log.warn("扫描待清理日志目录失败：{}", directory, ex);
            return;
        }
        for (Path file : candidates) {
            if (activeFileName != null && activeFileName.equals(file.getFileName().toString())) {
                continue;
            }
            try {
                if (Files.getLastModifiedTime(file).toInstant().isBefore(threshold)) {
                    Files.delete(file);
                    deleted.add(file);
                }
            } catch (IOException ex) {
                log.warn("清理过期日志文件失败：{}", file, ex);
            }
        }
    }
}
