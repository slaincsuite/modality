package cn.slainc.logger.archive;

import cn.slainc.logger.autoconfigure.LoggerProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogArchiveServiceTest {

    @TempDir
    Path tempDir;

    private static final String ACTIVE_FILE = "modality.log";

    private LoggerProperties.Archive archiveProperties() {
        LoggerProperties properties = new LoggerProperties();
        properties.getArchive().setColdThresholdDays(7);
        properties.getArchive().setRetentionDays(30);
        properties.getArchive().setArchiveRetentionDays(365);
        properties.getArchive().setCycle("DAY");
        properties.getArchive().setGzip(true);
        properties.getArchive().setDeleteAfterArchive(true);
        return properties.getArchive();
    }

    @Test
    void archivesColdLogFileAsGzipAndKeepsActiveFile() throws IOException {
        Path logs = tempDir.resolve("logs");
        Files.createDirectories(logs);
        Path cold = logs.resolve("modality.log.2025-01-01.0.log");
        Files.writeString(cold, "{\"message\":\"历史日志\"}" + System.lineSeparator(), StandardCharsets.UTF_8);
        Files.setLastModifiedTime(cold, FileTime.from(Instant.now().minus(30, ChronoUnit.DAYS)));
        Path active = logs.resolve(ACTIVE_FILE);
        Files.writeString(active, "{\"message\":\"当前日志\"}" + System.lineSeparator(), StandardCharsets.UTF_8);
        Files.setLastModifiedTime(active, FileTime.from(Instant.now().minus(30, ChronoUnit.DAYS)));

        LogArchiveService archiveService = new LogArchiveService(archiveProperties(), logs, ACTIVE_FILE);
        List<Path> produced = archiveService.archive();

        assertEquals(1, produced.size(), "只应归档冷的历史轮转文件");
        Path gz = produced.get(0);
        assertTrue(gz.getFileName().toString().endsWith(".log.gz"), "归档产物必须是 GZIP：" + gz);
        assertTrue(Files.exists(gz));
        try (InputStream input = new GZIPInputStream(Files.newInputStream(gz))) {
            assertEquals("{\"message\":\"历史日志\"}", new String(input.readAllBytes(), StandardCharsets.UTF_8).trim());
        }
        assertFalse(Files.exists(cold), "归档后冷文件必须被移走");
        assertTrue(Files.exists(active), "正在写入的活动日志文件不能被搬走");
        assertTrue(gz.getParent().getFileName().toString().matches("\\d{4}-\\d{2}-\\d{2}"), "按天归档目录格式不对");
    }

    @Test
    void keepsHotFilesUntouched() throws IOException {
        Path logs = tempDir.resolve("logs");
        Files.createDirectories(logs);
        Path hot = logs.resolve("modality.log.2026-10-12.0.log");
        Files.writeString(hot, "{\"message\":\"刚轮转的日志\"}" + System.lineSeparator(), StandardCharsets.UTF_8);

        LogArchiveService archiveService = new LogArchiveService(archiveProperties(), logs, ACTIVE_FILE);
        assertTrue(archiveService.archive().isEmpty(), "未超过冷阈值的文件不应归档");
        assertTrue(Files.exists(hot));
    }

    @Test
    void cleanupDeletesExpiredHotAndArchiveFiles() throws IOException {
        Path logs = tempDir.resolve("logs");
        Files.createDirectories(logs);
        LoggerProperties.Archive archive = archiveProperties();
        LogArchiveService archiveService = new LogArchiveService(archive, logs, ACTIVE_FILE);
        Path archiveRoot = archiveService.archiveDirectory();
        Path expiredArchive = archiveRoot.resolve("2024-01-01").resolve("modality.log.2024-01-01.0.log.gz");
        Files.createDirectories(expiredArchive.getParent());
        Files.writeString(expiredArchive, "old", StandardCharsets.UTF_8);
        Files.setLastModifiedTime(expiredArchive, FileTime.from(Instant.now().minus(400, ChronoUnit.DAYS)));
        Path freshArchive = archiveRoot.resolve("2026-10-12").resolve("modality.log.2026-10-12.0.log.gz");
        Files.createDirectories(freshArchive.getParent());
        Files.writeString(freshArchive, "new", StandardCharsets.UTF_8);
        Path expiredHot = logs.resolve("modality.log.2026-01-01.0.log");
        Files.writeString(expiredHot, "old", StandardCharsets.UTF_8);
        Files.setLastModifiedTime(expiredHot, FileTime.from(Instant.now().minus(40, ChronoUnit.DAYS)));
        Path active = logs.resolve(ACTIVE_FILE);
        Files.writeString(active, "active", StandardCharsets.UTF_8);
        Files.setLastModifiedTime(active, FileTime.from(Instant.now().minus(40, ChronoUnit.DAYS)));

        LogCleanupService cleanupService = new LogCleanupService(archive, logs, archiveRoot, ACTIVE_FILE);
        List<Path> deleted = cleanupService.cleanup();

        assertTrue(deleted.contains(expiredArchive), "超过归档保留期的 gz 必须删除");
        assertTrue(deleted.contains(expiredHot), "超过热保留期的热文件必须删除");
        assertFalse(Files.exists(expiredArchive));
        assertFalse(Files.exists(expiredHot));
        assertTrue(Files.exists(freshArchive), "未超期的归档必须保留");
        assertTrue(Files.exists(active), "活动日志文件必须保留");
    }

    @Test
    void maintenanceSchedulerRunsArchiveAndCleanup() throws IOException {
        Path logs = tempDir.resolve("logs");
        Files.createDirectories(logs);
        Path cold = logs.resolve("modality.log.2025-05-05.0.log");
        Files.writeString(cold, "{\"message\":\"历史\"}" + System.lineSeparator(), StandardCharsets.UTF_8);
        Files.setLastModifiedTime(cold, FileTime.from(Instant.now().minus(20, ChronoUnit.DAYS)));
        LoggerProperties.Archive archive = archiveProperties();
        archive.setIntervalMinutes(0L);
        LogArchiveService archiveService = new LogArchiveService(archive, logs, ACTIVE_FILE);
        LogCleanupService cleanupService = new LogCleanupService(archive, logs,
                archiveService.archiveDirectory(), ACTIVE_FILE);
        java.util.concurrent.atomic.AtomicInteger refreshed = new java.util.concurrent.atomic.AtomicInteger();
        LogMaintenanceScheduler scheduler = new LogMaintenanceScheduler(archive, archiveService, cleanupService,
                List.of(refreshed::incrementAndGet));

        LogMaintenanceScheduler.MaintenanceResult result = scheduler.runOnce();
        scheduler.destroy();

        assertEquals(1, result.archived().size());
        assertEquals(1, refreshed.get(), "维护任务必须同时刷新动态配置");
        assertTrue(Files.exists(result.archived().get(0)));
    }
}
