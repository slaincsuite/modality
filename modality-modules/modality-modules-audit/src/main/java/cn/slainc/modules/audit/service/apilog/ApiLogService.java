package cn.slainc.modules.audit.service.apilog;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;
import cn.slainc.modules.audit.service.oplog.AuditLogService;

import cn.slainc.core.result.PageResult;
import cn.slainc.modules.audit.entity.ApiLogRecord;
import cn.slainc.modules.audit.mapper.ApiLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ApiLogService {

    private static final Logger log = LoggerFactory.getLogger(ApiLogService.class);

    private final ApiLogMapper apiLogMapper;

    public ApiLogService(ApiLogMapper apiLogMapper) {
        this.apiLogMapper = apiLogMapper;
    }

    public PageResult<ApiLogRecord> search(String path, String method, Integer statusCode, String statusRange,
                                           String loginId, String loginType, Long from, Long to, long current,
                                           long size) {
        Page<ApiLogRecord> page = apiLogMapper.selectPage(Page.of(current, size),
                baseWrapper(path, method, statusCode, statusRange, loginId, loginType, from, to)
                        .orderByDesc(ApiLogRecord::getId));
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public PageResult<ApiLogRecord> slowRequests(Long from, Long to, long current, long size) {
        Page<ApiLogRecord> page = apiLogMapper.selectPage(Page.of(current, size),
                baseWrapper(null, null, null, null, null, null, from, to)
                        .eq(ApiLogRecord::getSlow, true)
                        .orderByDesc(ApiLogRecord::getCostMillis));
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public PageResult<ApiLogRecord> errorRequests(Long from, Long to, long current, long size) {
        Page<ApiLogRecord> page = apiLogMapper.selectPage(Page.of(current, size),
                baseWrapper(null, null, null, null, null, null, from, to)
                        .eq(ApiLogRecord::getError, true)
                        .orderByDesc(ApiLogRecord::getId));
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public List<ApiLogRecord> list(String path, String method, Integer statusCode, String statusRange, String scope,
                                   String loginId, String loginType, Long from, Long to, int limit) {
        return apiLogMapper.selectList(applyScope(baseWrapper(path, method, statusCode, statusRange, loginId,
                                loginType, from, to), scope)
                .orderByDesc(ApiLogRecord::getId)
                .last("LIMIT " + AuditLogService.clampLimit(limit)));
    }

    public Map<String, Object> requestStats(Long from, Long to) {
        List<Map<String, Object>> rows = apiLogMapper.selectMaps(new QueryWrapper<ApiLogRecord>()
                .select("COUNT(*) AS total", "SUM(CASE WHEN error = 1 THEN 1 ELSE 0 END) AS errors",
                        "SUM(CASE WHEN slow = 1 THEN 1 ELSE 0 END) AS slow")
                .ge(from != null, "create_time", from)
                .le(to != null, "create_time", to));
        Map<String, Object> row = rows.isEmpty() ? Map.of() : rows.get(0);
        long total = countValue(row, "TOTAL");
        long errors = countValue(row, "ERRORS");
        long slow = countValue(row, "SLOW");
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", total);
        stats.put("errors", errors);
        stats.put("slow", slow);
        stats.put("errorRate", total == 0 ? 0 : (double) errors / total);
        return stats;
    }

    private static long countValue(Map<String, Object> row, String key) {
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue() instanceof Number number ? number.longValue() : 0L;
            }
        }
        return 0L;
    }

    public int purgeBefore(long epochMillis) {
        try {
            return apiLogMapper.delete(new LambdaQueryWrapper<ApiLogRecord>()
                    .lt(ApiLogRecord::getCreateTime, epochMillis));
        } catch (Exception e) {
            log.warn("API 访问日志清理失败: {} {}", e.getClass().getName(), e.getMessage());
            return 0;
        }
    }

    private LambdaQueryWrapper<ApiLogRecord> baseWrapper(String path, String method, Integer statusCode,
                                                         String statusRange, String loginId, String loginType,
                                                         Long from, Long to) {
        LambdaQueryWrapper<ApiLogRecord> wrapper = new LambdaQueryWrapper<ApiLogRecord>()
                .like(path != null && !path.isBlank(), ApiLogRecord::getRequestPath, path)
                .eq(method != null && !method.isBlank(), ApiLogRecord::getHttpMethod, method)
                .eq(statusCode != null, ApiLogRecord::getStatusCode, statusCode)
                .eq(loginId != null && !loginId.isBlank(), ApiLogRecord::getLoginId, loginId)
                .eq(loginType != null && !loginType.isBlank(), ApiLogRecord::getLoginType, loginType)
                .ge(from != null, ApiLogRecord::getCreateTime, from)
                .le(to != null, ApiLogRecord::getCreateTime, to);
        return applyStatusRange(wrapper, statusCode, statusRange);
    }

    private LambdaQueryWrapper<ApiLogRecord> applyStatusRange(LambdaQueryWrapper<ApiLogRecord> wrapper,
                                                              Integer statusCode, String statusRange) {
        if (statusCode != null || statusRange == null || statusRange.isBlank()) {
            return wrapper;
        }
        return switch (statusRange.trim().toLowerCase(Locale.ROOT)) {
            case "4xx" -> wrapper.ge(ApiLogRecord::getStatusCode, 400).lt(ApiLogRecord::getStatusCode, 500);
            case "5xx" -> wrapper.ge(ApiLogRecord::getStatusCode, 500).lt(ApiLogRecord::getStatusCode, 600);
            default -> throw new BizException(ResultCode.BAD_REQUEST, "状态范围只允许 4xx 或 5xx");
        };
    }

    private LambdaQueryWrapper<ApiLogRecord> applyScope(LambdaQueryWrapper<ApiLogRecord> wrapper, String scope) {
        if (scope == null || scope.isBlank() || "all".equalsIgnoreCase(scope)) {
            return wrapper;
        }
        return switch (scope.trim().toLowerCase(Locale.ROOT)) {
            case "slow" -> wrapper.eq(ApiLogRecord::getSlow, true);
            case "error" -> wrapper.eq(ApiLogRecord::getError, true);
            default -> throw new BizException(ResultCode.BAD_REQUEST, "导出范围只允许 all、slow 或 error");
        };
    }
}
