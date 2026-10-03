package cn.slainc.modules.infra.service;

import cn.slainc.core.lang.BizAssert;
import cn.slainc.core.result.PageResult;
import cn.slainc.logger.archive.LogArchiveService;
import cn.slainc.logger.level.LogLevelManager;
import cn.slainc.logger.query.LogEntry;
import cn.slainc.logger.query.LogFileQueryService;
import cn.slainc.logger.query.LogQuery;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    private final LogFileQueryService queryService;

    private final ObjectProvider<LogLevelManager> levelManager;

    private final ObjectProvider<LogArchiveService> archiveService;

    public LogConsoleService(LogFileQueryService queryService, ObjectProvider<LogLevelManager> levelManager,
                             ObjectProvider<LogArchiveService> archiveService) {
        this.queryService = queryService;
        this.levelManager = levelManager;
        this.archiveService = archiveService;
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
        long safeCurrent = Math.max(current, 1);
        int safeSize = (int) Math.min(Math.max(size, 1), 500);
        query.setLimit((int) (safeCurrent * safeSize));
        List<LogEntry> entries = queryService.query(query);
        return PageResult.from(entries, safeCurrent, safeSize);
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

    public byte[] readFile(Path path) throws IOException {
        return Files.readAllBytes(path);
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
