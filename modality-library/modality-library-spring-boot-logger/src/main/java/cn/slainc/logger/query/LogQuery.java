package cn.slainc.logger.query;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

public class LogQuery {

    private String keyword;

    private String loggerPrefix;

    private String traceId;

    private Long userId;

    private Long tenantId;

    private final Set<String> levels = new LinkedHashSet<>();

    private Instant from;

    private Instant to;

    private int offset = 0;

    private int limit = 200;

    private boolean includeArchived = false;

    public String getKeyword() {
        return keyword;
    }

    public LogQuery setKeyword(String keyword) {
        this.keyword = keyword;
        return this;
    }

    public String getLoggerPrefix() {
        return loggerPrefix;
    }

    public LogQuery setLoggerPrefix(String loggerPrefix) {
        this.loggerPrefix = loggerPrefix;
        return this;
    }

    public String getTraceId() {
        return traceId;
    }

    public LogQuery setTraceId(String traceId) {
        this.traceId = traceId;
        return this;
    }

    public Long getUserId() {
        return userId;
    }

    public LogQuery setUserId(Long userId) {
        this.userId = userId;
        return this;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public LogQuery setTenantId(Long tenantId) {
        this.tenantId = tenantId;
        return this;
    }

    public Set<String> getLevels() {
        return levels;
    }

    public LogQuery setLevels(Set<String> levels) {
        this.levels.clear();
        if (levels != null) {
            for (String level : levels) {
                if (level != null && !level.isBlank()) {
                    this.levels.add(level.trim().toUpperCase(java.util.Locale.ROOT));
                }
            }
        }
        return this;
    }

    public LogQuery addLevel(String level) {
        if (level != null && !level.isBlank()) {
            this.levels.add(level.trim().toUpperCase(java.util.Locale.ROOT));
        }
        return this;
    }

    public Instant getFrom() {
        return from;
    }

    public LogQuery setFrom(Instant from) {
        this.from = from;
        return this;
    }

    public Instant getTo() {
        return to;
    }

    public LogQuery setTo(Instant to) {
        this.to = to;
        return this;
    }

    public int getOffset() {
        return Math.max(0, offset);
    }

    public LogQuery setOffset(int offset) {
        this.offset = offset;
        return this;
    }

    public int getLimit() {
        return limit;
    }

    public LogQuery setLimit(int limit) {
        this.limit = limit;
        return this;
    }

    public boolean isIncludeArchived() {
        return includeArchived;
    }

    public LogQuery setIncludeArchived(boolean includeArchived) {
        this.includeArchived = includeArchived;
        return this;
    }
}
