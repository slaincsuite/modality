package cn.slainc.modules.audit.service.oplog;

import cn.slainc.core.result.PageResult;
import cn.slainc.modules.audit.entity.AuditLogRecord;
import cn.slainc.modules.audit.mapper.AuditLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    public static final int MAX_EXPORT_LIMIT = 50000;

    private static final int MAX_TOP_USERS = 1000;

    private final AuditLogMapper auditLogMapper;

    public AuditLogService(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    public PageResult<AuditLogRecord> search(String category, String operation, String level, String module,
                                              String username, String description, String loginType, Boolean success,
                                              Long from, Long to, long current, long size) {
        Page<AuditLogRecord> page = auditLogMapper.selectPage(Page.of(current, size),
                baseWrapper(category, operation, level, module, username, description, loginType, success, from, to)
                        .orderByDesc(AuditLogRecord::getId));
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public List<AuditLogRecord> list(String category, String operation, String level, String module, String username,
                                     String description, String loginType, Boolean success, Long from, Long to,
                                     int limit) {
        return auditLogMapper.selectList(baseWrapper(category, operation, level, module, username, description,
                        loginType, success, from, to)
                .orderByDesc(AuditLogRecord::getId)
                .last("LIMIT " + clampLimit(limit)));
    }

    public static int clampLimit(int limit) {
        return Math.min(Math.max(limit, 1), MAX_EXPORT_LIMIT);
    }

    public long count(Long from, Long to, Boolean success, String category) {
        return auditLogMapper.selectCount(baseWrapper(category, null, null, null, null, null, null, success, from, to));
    }

    public long countLevelIn(Long from, Long to, List<String> levels) {
        return auditLogMapper.selectCount(new LambdaQueryWrapper<AuditLogRecord>()
                .in(AuditLogRecord::getLevel, levels)
                .ge(from != null, AuditLogRecord::getCreateTime, from)
                .le(to != null, AuditLogRecord::getCreateTime, to));
    }

    public Map<String, Object> loginStats(Long from, Long to) {
        List<Map<String, Object>> rows = auditLogMapper.selectMaps(new QueryWrapper<AuditLogRecord>()
                .select("COUNT(*) AS total", "SUM(CASE WHEN success = 0 THEN 1 ELSE 0 END) AS failed")
                .eq("category", "LOGIN")
                .ge(from != null, "create_time", from)
                .le(to != null, "create_time", to));
        Map<String, Object> row = rows.isEmpty() ? Map.of() : lowerKeys(rows.get(0));
        long total = toLong(row.get("total"));
        long failed = toLong(row.get("failed"));
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", total);
        stats.put("success", total - failed);
        stats.put("failed", failed);
        stats.put("failureRate", total == 0 ? 0 : (double) failed / total);
        return stats;
    }

    public List<Map<String, Object>> loginTrend(Long from, Long to) {
        Long safeFrom = from == null ? LocalDate.now().minusDays(6).atStartOfDay(ZoneId.systemDefault())
                .toInstant().toEpochMilli() : from;
        Long safeTo = to == null ? System.currentTimeMillis() : to;
        // 按天分桶下推到 SQL：只回传每天一行，避免把窗口内全量日志拉进内存
        long offsetMillis = ZoneId.systemDefault().getRules()
                .getOffset(java.time.Instant.ofEpochMilli((safeFrom + safeTo) / 2L)).getTotalSeconds() * 1000L;
        List<Map<String, Object>> rows = auditLogMapper.selectMaps(new QueryWrapper<AuditLogRecord>()
                .select("FLOOR((create_time + " + offsetMillis + ") / 86400000) AS day_bucket",
                        "COUNT(*) AS total", "SUM(CASE WHEN success = 0 THEN 1 ELSE 0 END) AS failed")
                .eq("category", "LOGIN")
                .ge("create_time", safeFrom)
                .le("create_time", safeTo)
                .groupBy("day_bucket")
                .orderByAsc("day_bucket"));
        List<Map<String, Object>> trend = new ArrayList<>(rows.size());
        for (Map<String, Object> raw : rows) {
            Map<String, Object> row = lowerKeys(raw);
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", LocalDate.ofEpochDay(toLong(row.get("day_bucket"))).toString());
            point.put("total", toLong(row.get("total")));
            point.put("failed", toLong(row.get("failed")));
            trend.add(point);
        }
        return trend;
    }

    public Map<String, Long> categoryDistribution(Long from, Long to) {
        List<Map<String, Object>> rows = auditLogMapper.selectMaps(new QueryWrapper<AuditLogRecord>()
                .select("category AS category", "COUNT(*) AS total")
                .ge(from != null, "create_time", from)
                .le(to != null, "create_time", to)
                .groupBy("category")
                .orderByAsc("category"));
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Map<String, Object> raw : rows) {
            Map<String, Object> row = lowerKeys(raw);
            Object category = row.get("category");
            counts.put(category == null ? "OTHER" : String.valueOf(category), toLong(row.get("total")));
        }
        return counts;
    }

    public List<Map<String, Object>> activeUsers(Long from, Long to, int topN) {
        List<Map<String, Object>> rows = auditLogMapper.selectMaps(new QueryWrapper<AuditLogRecord>()
                .select("username AS username", "COUNT(*) AS total")
                .isNotNull("username")
                .ge(from != null, "create_time", from)
                .le(to != null, "create_time", to)
                .groupBy("username")
                .orderByDesc("total")
                .last("LIMIT " + Math.min(Math.max(topN, 1), MAX_TOP_USERS)));
        List<Map<String, Object>> users = new ArrayList<>(rows.size());
        for (Map<String, Object> raw : rows) {
            Map<String, Object> row = lowerKeys(raw);
            Map<String, Object> user = new LinkedHashMap<>();
            user.put("username", row.get("username"));
            user.put("operations", toLong(row.get("total")));
            users.add(user);
        }
        return users;
    }

    private static Map<String, Object> lowerKeys(Map<String, Object> row) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        row.forEach((key, value) -> normalized.put(key == null ? "" : key.toLowerCase(Locale.ROOT), value));
        return normalized;
    }

    private static long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private LambdaQueryWrapper<AuditLogRecord> baseWrapper(String category, String operation, String level,
                                                           String module, String username, String description,
                                                           String loginType, Boolean success, Long from, Long to) {
        return new LambdaQueryWrapper<AuditLogRecord>()
                .eq(category != null && !category.isBlank(), AuditLogRecord::getCategory, category)
                .eq(operation != null && !operation.isBlank(), AuditLogRecord::getOperation, operation)
                .eq(level != null && !level.isBlank(), AuditLogRecord::getLevel, level)
                .eq(module != null && !module.isBlank(), AuditLogRecord::getModule, module)
                .like(username != null && !username.isBlank(), AuditLogRecord::getUsername, trim(username))
                .like(description != null && !description.isBlank(), AuditLogRecord::getDescription,
                        trim(description))
                .eq(loginType != null && !loginType.isBlank(), AuditLogRecord::getLoginType, loginType)
                .eq(success != null, AuditLogRecord::getSuccess, success)
                .ge(from != null, AuditLogRecord::getCreateTime, from)
                .le(to != null, AuditLogRecord::getCreateTime, to);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    public int purgeBefore(long epochMillis) {
        try {
            return auditLogMapper.delete(new LambdaQueryWrapper<AuditLogRecord>()
                    .lt(AuditLogRecord::getCreateTime, epochMillis));
        } catch (Exception e) {
            log.warn("审计日志清理失败: {} {}", e.getClass().getName(), e.getMessage());
            return 0;
        }
    }

    public void insert(AuditLogRecord record) {
        try {
            auditLogMapper.insert(record);
        } catch (Exception e) {
            log.warn("审计日志落库失败: {} {}", e.getClass().getName(), e.getMessage());
        }
    }
}
