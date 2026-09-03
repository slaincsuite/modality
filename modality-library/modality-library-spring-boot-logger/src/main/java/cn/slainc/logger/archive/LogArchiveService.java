package cn.slainc.logger.archive;

import cn.slainc.logger.autoconfigure.LoggerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

public class LogArchiveService {

    private static final Logger log = LoggerFactory.getLogger(LogArchiveService.class);

    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    private final LoggerProperties.Archive properties;

    private final Path hotDirectory;

    private final String activeFileName;

    public LogArchiveService(LoggerProperties.Archive properties, Path hotDirectory, String activeFileName) {
        this.properties = properties;
        this.hotDirectory = hotDirectory;
        this.activeFileName = activeFileName;
    }

    public Path hotDirectory() {
        return hotDirectory;
    }

    public Path archiveDirectory() {
        String configured = properties.getArchiveDir();
        return configured == null || configured.isBlank()
                ? hotDirectory.resolve("archive") : Path.of(configured).toAbsolutePath().normalize();
    }

    public List<Path> archive() {
        return archive(Instant.now());
    }

    public List<Path> archive(Instant now) {
        if (!properties.isEnabled() || !Files.isDirectory(hotDirectory)) {
            return List.of();
        }
        Instant coldThreshold = now.minus(properties.getColdThresholdDays(), ChronoUnit.DAYS);
        List<Path> produced = new ArrayList<>();
        List<Path> candidates;
        try (Stream<Path> stream = Files.list(hotDirectory)) {
            candidates = stream.filter(Files::isRegularFile).sorted().toList();
        } catch (IOException ex) {
            throw new IllegalStateException("扫描日志目录失败：" + hotDirectory, ex);
        }
        for (Path source : candidates) {
            // 正在被 Logback 持有的活动日志文件不能移动：移动后 appender 仍写旧 inode，会把归档产物写坏
            if (activeFileName != null && activeFileName.equals(source.getFileName().toString())) {
                continue;
            }
            try {
                Instant modified = Files.getLastModifiedTime(source).toInstant();
                if (!modified.isBefore(coldThreshold)) {
                    continue;
                }
                Path target = resolveTarget(source, modified);
                Files.createDirectories(target.getParent());
                writeArchive(source, target);
                produced.add(target);
                if (properties.isDeleteAfterArchive()) {
                    Files.delete(source);
                }
            } catch (IOException ex) {
                log.warn("归档日志文件失败：{}", source, ex);
            }
        }
        return produced;
    }

    private Path resolveTarget(Path source, Instant modified) {
        String subDirectory = "MONTH".equalsIgnoreCase(properties.getCycle())
                ? MONTH_FORMATTER.format(modified.atZone(ZoneId.systemDefault()))
                : DAY_FORMATTER.format(modified.atZone(ZoneId.systemDefault()));
        String fileName = source.getFileName().toString();
        String archivedName = properties.isGzip() && !fileName.endsWith(".gz") ? fileName + ".gz" : fileName;
        return archiveDirectory().resolve(subDirectory).resolve(archivedName);
    }

    private void writeArchive(Path source, Path target) throws IOException {
        boolean compress = target.getFileName().toString().endsWith(".gz")
                && !source.getFileName().toString().endsWith(".gz");
        if (compress) {
            try (InputStream input = Files.newInputStream(source);
                 OutputStream output = new GZIPOutputStream(Files.newOutputStream(target))) {
                input.transferTo(output);
            }
        } else {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
