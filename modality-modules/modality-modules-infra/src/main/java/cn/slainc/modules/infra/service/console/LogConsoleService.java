package cn.slainc.modules.infra.service.console;

import cn.slainc.core.lang.BizAssert;
import cn.slainc.core.result.PageResult;
import cn.slainc.logger.archive.LogArchiveService;
import cn.slainc.logger.level.LogLevelManager;
import cn.slainc.logger.query.LogEntry;
import cn.slainc.logger.query.LogFileQueryService;
import cn.slainc.logger.query.LogQuery;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;

public class LogConsoleService {

    private static final Set<String> LEVELS = Set.of("TRACE", "DEBUG", "INFO", "WARN", "ERROR");

    private static final int MAX_SCAN_ENTRIES = 20_000;

    private static final long MAX_CURRENT = 20_000;

    private static final Duration DEFAULT_CACHE_TTL = Duration.ofSeconds(30);

    private static final long DEFAULT_DOWNLOAD_MAX_BYTES = 32L * 1024 * 1024;

    private final LogFileQueryService queryService;

    private final ObjectProvider<LogLevelManager> levelManager;

    private final ObjectProvider<LogArchiveService> archiveService;

    private final Cache<String, List<LogEntry>> pageCache;

    private final long downloadMaxBytes;

    public LogConsoleService(LogFileQueryService queryService, ObjectProvider<LogLevelManager> levelManager,
                             ObjectProvider<LogArchiveService> archiveService) {
        this(queryService, levelManager, archiveService, DEFAULT_CACHE_TTL, 16, DEFAULT_DOWNLOAD_MAX_BYTES);
    }

    public LogConsoleService(LogFileQueryService queryService, ObjectProvider<LogLevelManager> levelManager,
                             ObjectProvider<LogArchiveService> archiveService, Duration cacheTtl, int cacheMaxQueries) {
        this(queryService, levelManager, archiveService, cacheTtl, cacheMaxQueries, DEFAULT_DOWNLOAD_MAX_BYTES);
    }

    public LogConsoleService(LogFileQueryService queryService, ObjectProvider<LogLevelManager> levelManager,
                             ObjectProvider<LogArchiveService> archiveService, Duration cacheTtl, int cacheMaxQueries,
                             long downloadMaxBytes) {
        this.queryService = queryService;
        this.levelManager = levelManager;
        this.archiveService = archiveService;
        this.downloadMaxBytes = downloadMaxBytes > 0 ? downloadMaxBytes : DEFAULT_DOWNLOAD_MAX_BYTES;
        this.pageCache = Caffeine.newBuilder()
                .maximumSize(Math.max(cacheMaxQueries, 1))
                .expireAfterWrite(cacheTtl == null || cacheTtl.isZero() || cacheTtl.isNegative()
                        ? DEFAULT_CACHE_TTL : cacheTtl)
                .build();
    }

    public PageResult<LogEntry> page(long current, long size, String keyword, String level, String loggerPrefix,
                                      String traceId, String from, String to, boolean includeArchived) {
        LogQuery query = new LogQuery();
        if (keyword != null && !keyword.isBlank()) {
            query.setKeyword(keyword.trim());
        }
        if (level != null && !level.isBlank()) {
            query.setLevels(Set.of(level.trim().toUpperCase(Locale.ROOT)));
        }
        if (loggerPrefix != null && !loggerPrefix.isBlank()) {
            query.setLoggerPrefix(loggerPrefix.trim());
        }
        if (traceId != null && !traceId.isBlank()) {
            query.setTraceId(traceId.trim());
        }
        Instant fromInstant = parseInstant(from);
        Instant toInstant = parseInstant(to);
        if (fromInstant != null) {
            query.setFrom(fromInstant);
        }
        if (toInstant != null) {
            query.setTo(toInstant);
        }
        query.setIncludeArchived(includeArchived);
        query.setLimit(MAX_SCAN_ENTRIES);
        long safeCurrent = Math.min(Math.max(current, 1), MAX_CURRENT);
        int safeSize = (int) Math.min(Math.max(size, 1), 500);
        List<LogEntry> scanned = pageCache.get(cacheKey(query), key -> queryService.query(query));
        if (scanned == null) {
            return PageResult.empty(safeCurrent, safeSize);
        }
        return PageResult.from(scanned, safeCurrent, safeSize);
    }

    public List<String> files(boolean includeArchived) {
        return queryService.logFiles(includeArchived).stream()
                .map(path -> path.getFileName().toString())
                .toList();
    }

    public Path download(String fileName) throws IOException {
        BizAssert.hasText(fileName, "日志文件名不能为空");
        BizAssert.isTrue(!fileName.contains("/") && !fileName.contains("\\") && !fileName.contains(".."),
                "日志文件名不合法");
        List<Path> candidates = queryService.logFiles(true);
        for (Path path : candidates) {
            if (path.getFileName().toString().equals(fileName)) {
                return path;
            }
        }
        throw new cn.slainc.core.exception.BizException(cn.slainc.core.result.ResultCode.DATA_NOT_FOUND,
                "日志文件不存在：" + fileName);
    }

    public boolean setLevel(String logger, String level) {
        LogLevelManager manager = requireLevelManager();
        BizAssert.hasText(logger, "日志名不能为空");
        BizAssert.hasText(level, "日志级别不能为空");
        String safeLevel = level.trim().toUpperCase(Locale.ROOT);
        BizAssert.isTrue(LEVELS.contains(safeLevel), "日志级别只允许 TRACE、DEBUG、INFO、WARN、ERROR");
        return manager.setLevel(logger.trim(), safeLevel);
    }

    public String getLevel(String logger) {
        return requireLevelManager().getLevel(logger);
    }

    public int refreshLevels() {
        return requireLevelManager().refresh();
    }

    public List<String> archive() {
        LogArchiveService service = archiveService.getIfAvailable();
        if (service == null) {
            throw new cn.slainc.core.exception.BizException(cn.slainc.core.result.ResultCode.SERVICE_UNAVAILABLE,
                    "日志归档服务未启用");
        }
        return service.archive().stream()
                .map(path -> path.getFileName().toString())
                .toList();
    }

    // 日志文件可能到 GB 级，先按大小拦截再流式输出，禁止整文件进堆
    public void downloadTo(Path path, OutputStream target) throws IOException {
        long size = Files.size(path);
        if (size > downloadMaxBytes) {
            throw new cn.slainc.core.exception.BizException(cn.slainc.core.result.ResultCode.PAYLOAD_TOO_LARGE,
                    "日志文件超过在线下载上限（" + downloadMaxBytes + " 字节），请走归档下载：" + path.getFileName());
        }
        try (java.io.InputStream input = Files.newInputStream(path)) {
            input.transferTo(target);
        }
    }

    private String cacheKey(LogQuery query) {
        return String.join("\u0001",
                nullToEmpty(query.getKeyword()),
                String.join(",", query.getLevels()),
                nullToEmpty(query.getLoggerPrefix()),
                nullToEmpty(query.getTraceId()),
                query.getFrom() == null ? "" : query.getFrom().toString(),
                query.getTo() == null ? "" : query.getTo().toString(),
                String.valueOf(query.isIncludeArchived()),
                fileFingerprint(query.isIncludeArchived()));
    }

    // 日志文件一旦追加或滚动就必须重解析，用文件名+大小+修改时间做指纹
    private String fileFingerprint(boolean includeArchived) {
        StringBuilder builder = new StringBuilder();
        for (Path path : queryService.logFiles(includeArchived)) {
            builder.append(path.getFileName()).append(':').append(fileSize(path)).append(':')
                    .append(lastModified(path)).append(';');
        }
        return builder.toString();
    }

    private static long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException ex) {
            return -1L;
        }
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ex) {
            return -1L;
        }
    }

    private static String nullToEmpty(String text) {
        return text == null ? "" : text;
    }

    private LogLevelManager requireLevelManager() {
        LogLevelManager manager = levelManager.getIfAvailable();
        if (manager == null) {
            throw new cn.slainc.core.exception.BizException(cn.slainc.core.result.ResultCode.SERVICE_UNAVAILABLE,
                    "日志级别管理器未启用");
        }
        return manager;
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toInstant();
            } catch (DateTimeParseException exception) {
                throw new cn.slainc.core.exception.BizException("时间格式不合法（需要 ISO 即时时间或本地时间）："
                        + value);
            }
        }
    }
}
