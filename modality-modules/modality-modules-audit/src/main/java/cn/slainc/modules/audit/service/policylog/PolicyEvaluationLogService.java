package cn.slainc.modules.audit.service.policylog;

import cn.slainc.core.result.PageResult;
import cn.slainc.modules.audit.service.oplog.AuditLogService;
import cn.slainc.modules.audit.entity.PolicyEvaluationLogRecord;
import cn.slainc.modules.audit.mapper.PolicyEvaluationLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class PolicyEvaluationLogService {

    private static final Logger log = LoggerFactory.getLogger(PolicyEvaluationLogService.class);

    private final PolicyEvaluationLogMapper mapper;

    public PolicyEvaluationLogService(PolicyEvaluationLogMapper mapper) {
        this.mapper = mapper;
    }

    public PageResult<PolicyEvaluationLogRecord> search(String loginId, String loginType, String action,
                                                        String resource, String keyword, String decision, Long from,
                                                        Long to, long current, long size) {
        Page<PolicyEvaluationLogRecord> page = mapper.selectPage(Page.of(current, size),
                baseWrapper(loginId, loginType, action, resource, keyword, decision, from, to)
                        .orderByDesc(PolicyEvaluationLogRecord::getId));
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public List<PolicyEvaluationLogRecord> list(String loginId, String loginType, String action, String resource,
                                                String keyword, String decision, Long from, Long to, int limit) {
        return mapper.selectList(baseWrapper(loginId, loginType, action, resource, keyword, decision, from, to)
                .orderByDesc(PolicyEvaluationLogRecord::getId)
                .last("LIMIT " + AuditLogService.clampLimit(limit)));
    }

    public Map<String, Object> decisionStats(Long from, Long to) {
        List<Map<String, Object>> rows = mapper.selectMaps(new QueryWrapper<PolicyEvaluationLogRecord>()
                .select("COUNT(*) AS total", "SUM(CASE WHEN decision = 'DENY' THEN 1 ELSE 0 END) AS denied")
                .ge(from != null, "create_time", from)
                .le(to != null, "create_time", to));
        Map<String, Object> row = rows.isEmpty() ? Map.of() : rows.get(0);
        long total = countValue(row, "TOTAL");
        long denied = countValue(row, "DENIED");
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", total);
        stats.put("denied", denied);
        stats.put("denyRate", total == 0 ? 0 : (double) denied / total);
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

    public void insert(PolicyEvaluationLogRecord record) {
        try {
            mapper.insert(record);
        } catch (Exception e) {
            log.warn("策略评估日志落库失败: {} {}", e.getClass().getName(), e.getMessage());
        }
    }

    public int purgeBefore(long epochMillis) {
        try {
            return mapper.delete(new LambdaQueryWrapper<PolicyEvaluationLogRecord>()
                    .lt(PolicyEvaluationLogRecord::getCreateTime, epochMillis));
        } catch (Exception e) {
            log.warn("策略评估日志清理失败: {} {}", e.getClass().getName(), e.getMessage());
            return 0;
        }
    }

    private LambdaQueryWrapper<PolicyEvaluationLogRecord> baseWrapper(String loginId, String loginType, String action,
                                                                      String resource, String keyword,
                                                                      String decision, Long from, Long to) {
        String trimmedKeyword = keyword == null ? null : keyword.trim();
        return new LambdaQueryWrapper<PolicyEvaluationLogRecord>()
                .eq(loginId != null && !loginId.isBlank(), PolicyEvaluationLogRecord::getLoginId, loginId)
                .eq(loginType != null && !loginType.isBlank(), PolicyEvaluationLogRecord::getLoginType, loginType)
                .like(action != null && !action.isBlank(), PolicyEvaluationLogRecord::getAction, action)
                .like(resource != null && !resource.isBlank(), PolicyEvaluationLogRecord::getResource, resource)
                .and(trimmedKeyword != null && !trimmedKeyword.isEmpty(),
                        w -> w.like(PolicyEvaluationLogRecord::getLoginId, trimmedKeyword)
                                .or()
                                .like(PolicyEvaluationLogRecord::getAction, trimmedKeyword)
                                .or()
                                .like(PolicyEvaluationLogRecord::getResource, trimmedKeyword))
                .eq(decision != null && !decision.isBlank(), PolicyEvaluationLogRecord::getDecision, decision)
                .ge(from != null, PolicyEvaluationLogRecord::getCreateTime, from)
                .le(to != null, PolicyEvaluationLogRecord::getCreateTime, to);
    }
}
