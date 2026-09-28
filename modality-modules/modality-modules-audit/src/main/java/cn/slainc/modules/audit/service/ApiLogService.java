package cn.slainc.modules.audit.service;

import cn.slainc.core.result.PageResult;
import cn.slainc.modules.audit.entity.ApiLogRecord;
import cn.slainc.modules.audit.mapper.ApiLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ApiLogService {

    private static final Logger log = LoggerFactory.getLogger(ApiLogService.class);

    private final ApiLogMapper apiLogMapper;

    public ApiLogService(ApiLogMapper apiLogMapper) {
        this.apiLogMapper = apiLogMapper;
    }

    public PageResult<ApiLogRecord> search(String path, String method, Integer statusCode, String loginId,
                                           String loginType, Long from, Long to, long current, long size) {
        Page<ApiLogRecord> page = apiLogMapper.selectPage(Page.of(current, size),
                baseWrapper(path, method, statusCode, loginId, loginType, from, to)
                        .orderByDesc(ApiLogRecord::getId));
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public PageResult<ApiLogRecord> slowRequests(Long from, Long to, long current, long size) {
        Page<ApiLogRecord> page = apiLogMapper.selectPage(Page.of(current, size),
                baseWrapper(null, null, null, null, null, from, to)
                        .eq(ApiLogRecord::getSlow, true)
                        .orderByDesc(ApiLogRecord::getCostMillis));
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public PageResult<ApiLogRecord> errorRequests(Long from, Long to, long current, long size) {
        Page<ApiLogRecord> page = apiLogMapper.selectPage(Page.of(current, size),
                baseWrapper(null, null, null, null, null, from, to)
                        .eq(ApiLogRecord::getError, true)
                        .orderByDesc(ApiLogRecord::getId));
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public List<ApiLogRecord> list(String path, String method, Integer statusCode, String loginId, String loginType,
                                   Long from, Long to, int limit) {
        return apiLogMapper.selectList(baseWrapper(path, method, statusCode, loginId, loginType, from, to)
                .orderByDesc(ApiLogRecord::getId)
                .last("LIMIT " + Math.max(1, limit)));
    }

    public Map<String, Object> requestStats(Long from, Long to) {
        List<ApiLogRecord> records = apiLogMapper.selectList(baseWrapper(null, null, null, null, null, from, to));
        long total = records.size();
        long errors = records.stream().filter(record -> Boolean.TRUE.equals(record.getError())).count();
        long slow = records.stream().filter(record -> Boolean.TRUE.equals(record.getSlow())).count();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", total);
        stats.put("errors", errors);
        stats.put("slow", slow);
        stats.put("errorRate", total == 0 ? 0 : (double) errors / total);
        return stats;
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
                                                         String loginId, String loginType, Long from, Long to) {
        return new LambdaQueryWrapper<ApiLogRecord>()
                .like(path != null && !path.isBlank(), ApiLogRecord::getRequestPath, path)
                .eq(method != null && !method.isBlank(), ApiLogRecord::getHttpMethod, method)
                .eq(statusCode != null, ApiLogRecord::getStatusCode, statusCode)
                .eq(loginId != null && !loginId.isBlank(), ApiLogRecord::getLoginId, loginId)
                .eq(loginType != null && !loginType.isBlank(), ApiLogRecord::getLoginType, loginType)
                .ge(from != null, ApiLogRecord::getCreateTime, from)
                .le(to != null, ApiLogRecord::getCreateTime, to);
    }
}
