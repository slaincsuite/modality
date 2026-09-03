package cn.slainc.logger.query;

import cn.slainc.core.constant.SystemConstants;
import cn.slainc.logger.autoconfigure.LoggerProperties;
import cn.slainc.utils.json.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

public class LogFileQueryService {

    private static final Logger log = LoggerFactory.getLogger(LogFileQueryService.class);

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private static final String CSV_HEADER =
            "timestamp,level,logger,thread,traceId,spanId,userId,tenantId,message,exceptionClass,exceptionMessage,source";

    private final LoggerProperties.Query properties;

    private final Path logDirectory;

    private final Path archiveDirectory;

    public LogFileQueryService(LoggerProperties.Query properties, Path logDirectory, Path archiveDirectory) {
        this.properties = properties;
        this.logDirectory = logDirectory;
        this.archiveDirectory = archiveDirectory;
    }

    public List<Path> logFiles(boolean includeArchived) {
        List<Path> files = new ArrayList<>();
        collect(logDirectory, false, files);
        if (includeArchived) {
            collect(archiveDirectory, true, files);
        }
        files.sort(Comparator.comparing(path -> path.getFileName().toString()));
        if (files.size() > properties.getMaxFiles()) {
            return files.subList(files.size() - properties.getMaxFiles(), files.size());
        }
        return files;
    }

    public List<LogEntry> query(LogQuery query) {
        LogQuery effective = query == null ? new LogQuery() : query;
        List<LogEntry> matched = new ArrayList<>();
        for (Path file : logFiles(effective.isIncludeArchived() || properties.isIncludeArchived())) {
            readFile(file, effective, matched);
        }
        matched.sort(Comparator.comparing(LogEntry::timestamp, Comparator.nullsLast(Comparator.naturalOrder())));
        int offset = effective.getOffset();
        int limit = effective.getLimit() <= 0 ? properties.getMaxResults() : effective.getLimit();
        if (offset >= matched.size()) {
            return List.of();
        }
        return List.copyOf(matched.subList(offset, Math.min(matched.size(), offset + limit)));
    }

    public int exportCsv(LogQuery query, Path target) {
        List<LogEntry> entries = query(query);
        try {
            Path parent = target.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream output = Files.newOutputStream(target);
                 Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
                writer.write('\uFEFF');
                writer.write(toCsv(entries));
            }
        } catch (IOException ex) {
            throw new IllegalStateException("导出日志 CSV 失败：" + target, ex);
        }
        return entries.size();
    }

    public String toCsv(List<LogEntry> entries) {
        StringBuilder builder = new StringBuilder(CSV_HEADER).append('\n');
        for (LogEntry entry : entries) {
            builder.append(csv(entry.timestampText())).append(',')
                    .append(csv(entry.level())).append(',')
                    .append(csv(entry.logger())).append(',')
                    .append(csv(entry.thread())).append(',')
                    .append(csv(entry.traceId())).append(',')
                    .append(csv(entry.spanId())).append(',')
                    .append(csv(entry.userId())).append(',')
                    .append(csv(entry.tenantId())).append(',')
                    .append(csv(entry.message())).append(',')
                    .append(csv(entry.exceptionClass())).append(',')
                    .append(csv(entry.exceptionMessage())).append(',')
                    .append(csv(entry.source())).append('\n');
        }
        return builder.toString();
    }

    public static String csv(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\r", " ").replace("\n", " ");
        if (escaped.indexOf(',') >= 0 || escaped.indexOf('"') >= 0) {
            return '"' + escaped.replace("\"", "\"\"") + '"';
        }
        return escaped;
    }

    private void collect(Path directory, boolean recursive, List<Path> files) {
        if (directory == null || !Files.isDirectory(directory)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(directory, recursive ? Integer.MAX_VALUE : 1)) {
            stream.filter(Files::isRegularFile)
                    .filter(this::isLogFile)
                    .forEach(files::add);
        } catch (IOException ex) {
            log.warn("扫描日志文件失败：{}", directory, ex);
        }
    }

    private boolean isLogFile(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".log") || name.contains(".log.") || name.endsWith(".log.gz");
    }

    private void readFile(Path file, LogQuery query, List<LogEntry> matched) {
        try (InputStream raw = Files.newInputStream(file);
             InputStream input = file.getFileName().toString().endsWith(".gz") ? new GZIPInputStream(raw) : raw;
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            long read = 0L;
            while ((line = reader.readLine()) != null) {
                if (++read > properties.getMaxLinesPerFile()) {
                    log.warn("日志文件 {} 超过单文件读取上限 {} 行，剩余内容已跳过", file, properties.getMaxLinesPerFile());
                    break;
                }
                LogEntry entry = parse(line, file);
                if (entry != null && matches(entry, query)) {
                    matched.add(entry);
                }
            }
        } catch (IOException ex) {
            log.warn("读取日志文件失败：{}", file, ex);
        }
    }

    private LogEntry parse(String line, Path file) {
        if (line == null || line.isBlank() || line.charAt(0) != '{') {
            return null;
        }
        Map<String, Object> fields;
        try {
            fields = JsonUtils.parseMap(line);
        } catch (RuntimeException ex) {
            return null;
        }
        String timestampText = text(fields.get("timestamp"));
        Instant timestamp = parseTimestamp(timestampText);
        Map<String, Object> exception = fields.get("exception") instanceof Map<?, ?> map
                ? toStringKeyMap(map) : Map.of();
        return new LogEntry(timestamp, timestampText, text(fields.get("level")), text(fields.get("logger")),
                text(fields.get("thread")), text(fields.get("message")), text(fields.get("traceId")),
                text(fields.get("spanId")), text(fields.get("userId")), text(fields.get("tenantId")),
                text(exception.get("class")), text(exception.get("message")),
                file.getFileName().toString(), fields);
    }

    private boolean matches(LogEntry entry, LogQuery query) {
        if (!query.getLevels().isEmpty()
                && (entry.level() == null || !query.getLevels().contains(entry.level().toUpperCase(java.util.Locale.ROOT)))) {
            return false;
        }
        if (query.getLoggerPrefix() != null && !query.getLoggerPrefix().isBlank()
                && (entry.logger() == null || !entry.logger().startsWith(query.getLoggerPrefix()))) {
            return false;
        }
        if (query.getTraceId() != null && !query.getTraceId().isBlank()
                && !query.getTraceId().equals(entry.traceId())) {
            return false;
        }
        if (query.getUserId() != null && !query.getUserId().toString().equals(entry.userId())) {
            return false;
        }
        if (query.getTenantId() != null && !query.getTenantId().toString().equals(entry.tenantId())) {
            return false;
        }
        if (query.getFrom() != null && (entry.timestamp() == null || entry.timestamp().isBefore(query.getFrom()))) {
            return false;
        }
        if (query.getTo() != null && (entry.timestamp() == null || entry.timestamp().isAfter(query.getTo()))) {
            return false;
        }
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            String keyword = query.getKeyword();
            boolean hit = contains(entry.message(), keyword) || contains(entry.logger(), keyword)
                    || contains(entry.traceId(), keyword) || contains(entry.exceptionMessage(), keyword);
            if (!hit) {
                return false;
            }
        }
        return true;
    }

    private boolean contains(String text, String keyword) {
        return text != null && text.contains(keyword);
    }

    private static Instant parseTimestamp(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text, TIMESTAMP_FORMATTER)
                    .atZone(SystemConstants.DEFAULT_ZONE).toInstant();
        } catch (RuntimeException ex) {
            try {
                return Instant.parse(text);
            } catch (RuntimeException ignored) {
                return null;
            }
        }
    }

    private static Map<String, Object> toStringKeyMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
