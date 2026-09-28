package cn.slainc.modules.audit.service;

import cn.slainc.core.result.PageResult;
import cn.slainc.modules.audit.entity.AuditLogRecord;
import cn.slainc.modules.audit.mapper.AuditLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogMapper auditLogMapper;

    public AuditLogService(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    public PageResult<AuditLogRecord> search(String category, String operation, String level, String module,
                                              String username, String loginType, Boolean success, Long from, Long to,
                                              long current, long size) {
        Page<AuditLogRecord> page = auditLogMapper.selectPage(Page.of(current, size),
                baseWrapper(category, operation, level, module, username, loginType, success, from, to)
                        .orderByDesc(AuditLogRecord::getId));
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public List<AuditLogRecord> list(String category, String operation, String level, String module, String username,
                                     String loginType, Boolean success, Long from, Long to, int limit) {
        return auditLogMapper.selectList(baseWrapper(category, operation, level, module, username, loginType,
                        success, from, to)
                .orderByDesc(AuditLogRecord::getId)
                .last("LIMIT " + Math.max(1, limit)));
    }

    public long count(Long from, Long to, Boolean success, String category) {
        return auditLogMapper.selectCount(baseWrapper(category, null, null, null, null, null, success, from, to));
    }

    public long countLevelIn(Long from, Long to, List<String> levels) {
        return auditLogMapper.selectCount(new LambdaQueryWrapper<AuditLogRecord>()
                .in(AuditLogRecord::getLevel, levels)
                .ge(from != null, AuditLogRecord::getCreateTime, from)
                .le(to != null, AuditLogRecord::getCreateTime, to));
    }

    public Map<String, Object> loginStats(Long from, Long to) {
        long total = count(from, to, null, "LOGIN");
        long failed = count(from, to, false, "LOGIN");
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
        List<AuditLogRecord> records = auditLogMapper.selectList(
                new LambdaQueryWrapper<AuditLogRecord>()
                        .eq(AuditLogRecord::getCategory, "LOGIN")
                        .ge(AuditLogRecord::getCreateTime, safeFrom)
                        .le(AuditLogRecord::getCreateTime, safeTo));
        Map<LocalDate, long[]> byDay = new LinkedHashMap<>();
        for (AuditLogRecord record : records) {
            if (record.getCreateTime() == null) {
                continue;
            }
            LocalDate day = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(record.getCreateTime()),
                    ZoneId.systemDefault()).toLocalDate();
            long[] counts = byDay.computeIfAbsent(day, key -> new long[2]);
            counts[0]++;
            if (!Boolean.TRUE.equals(record.getSuccess())) {
                counts[1]++;
            }
        }
        List<Map<String, Object>> trend = new ArrayList<>();
        byDay.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    Map<String, Object> point = new LinkedHashMap<>();
                    point.put("date", entry.getKey().toString());
                    point.put("total", entry.getValue()[0]);
                    point.put("failed", entry.getValue()[1]);
                    trend.add(point);
                });
        return trend;
    }

    public Map<String, Long> categoryDistribution(Long from, Long to) {
        List<AuditLogRecord> records = auditLogMapper.selectList(baseWrapper(null, null, null, null, null, null,
                null, from, to));
        Map<String, Long> counts = new LinkedHashMap<>();
        for (AuditLogRecord record : records) {
            counts.merge(record.getCategory() == null ? "OTHER" : record.getCategory(), 1L, Long::sum);
        }
        return counts;
    }

    public List<Map<String, Object>> activeUsers(Long from, Long to, int topN) {
        List<AuditLogRecord> records = auditLogMapper.selectList(baseWrapper(null, null, null, null, null, null,
                null, from, to));
        Map<String, Long> counts = new LinkedHashMap<>();
        for (AuditLogRecord record : records) {
            if (record.getUsername() != null) {
                counts.merge(record.getUsername(), 1L, Long::sum);
            }
        }
        List<Map<String, Object>> users = new ArrayList<>();
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(Math.max(1, topN))
                .forEach(entry -> {
                    Map<String, Object> user = new LinkedHashMap<>();
                    user.put("username", entry.getKey());
                    user.put("operations", entry.getValue());
                    users.add(user);
                });
        return users;
    }

    private LambdaQueryWrapper<AuditLogRecord> baseWrapper(String category, String operation, String level,
                                                           String module, String username, String loginType,
                                                           Boolean success, Long from, Long to) {
        return new LambdaQueryWrapper<AuditLogRecord>()
                .eq(category != null && !category.isBlank(), AuditLogRecord::getCategory, category)
                .eq(operation != null && !operation.isBlank(), AuditLogRecord::getOperation, operation)
                .eq(level != null && !level.isBlank(), AuditLogRecord::getLevel, level)
                .eq(module != null && !module.isBlank(), AuditLogRecord::getModule, module)
                .like(username != null && !username.isBlank(), AuditLogRecord::getUsername, username)
                .eq(loginType != null && !loginType.isBlank(), AuditLogRecord::getLoginType, loginType)
                .eq(success != null, AuditLogRecord::getSuccess, success)
                .ge(from != null, AuditLogRecord::getCreateTime, from)
                .le(to != null, AuditLogRecord::getCreateTime, to);
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
