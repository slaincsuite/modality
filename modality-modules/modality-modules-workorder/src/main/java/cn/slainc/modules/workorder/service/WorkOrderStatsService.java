package cn.slainc.modules.workorder.service;

import cn.slainc.modules.workorder.entity.WorkOrderRecord;
import cn.slainc.modules.workorder.mapper.WorkOrderMapper;
import cn.slainc.workorder.enums.WorkOrderStatus;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;


public class WorkOrderStatsService {

    private final WorkOrderMapper workOrderMapper;

    public WorkOrderStatsService(WorkOrderMapper workOrderMapper) {
        this.workOrderMapper = workOrderMapper;
    }

    public Map<String, Object> overview() {
        List<Map<String, Object>> rows = workOrderMapper.selectMaps(new QueryWrapper<WorkOrderRecord>()
                .select("status AS name", "COUNT(*) AS total")
                .groupBy("status"));
        Map<String, Long> counts = new LinkedHashMap<>();
        long total = 0;
        for (Map<String, Object> raw : rows) {
            Map<String, Object> row = lowerKeys(raw);
            long value = toLong(row.get("total"));
            counts.put(String.valueOf(row.get("name")), value);
            total += value;
        }
        long startOfToday = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("total", total);
        overview.put("todayNew", workOrderMapper.selectCount(new LambdaQueryWrapper<WorkOrderRecord>()
                .ge(WorkOrderRecord::getCreateTime, startOfToday)));
        overview.put("open", counts.getOrDefault(WorkOrderStatus.OPEN.name(), 0L));
        overview.put("inProgress", counts.getOrDefault(WorkOrderStatus.IN_PROGRESS.name(), 0L));
        overview.put("resolved", counts.getOrDefault(WorkOrderStatus.RESOLVED.name(), 0L));
        overview.put("closed", counts.getOrDefault(WorkOrderStatus.CLOSED.name(), 0L));
        return overview;
    }

    public List<Map<String, Object>> byStatus() {
        return distributions("status");
    }

    public List<Map<String, Object>> byPriority() {
        return distributions("priority");
    }

    public List<Map<String, Object>> byType() {
        return distributions("type_code");
    }

    public List<Map<String, Object>> assigneeRanking(int limit) {
        List<Map<String, Object>> rows = workOrderMapper.selectMaps(new QueryWrapper<WorkOrderRecord>()
                .select("assignee_id AS name", "COUNT(*) AS total")
                .isNotNull("assignee_id")
                .groupBy("assignee_id")
                .orderByDesc("total")
                .last("LIMIT " + Math.max(limit, 1)));
        List<Map<String, Object>> ranking = new ArrayList<>(rows.size());
        for (Map<String, Object> raw : rows) {
            Map<String, Object> row = lowerKeys(raw);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", String.valueOf(row.get("name")));
            item.put("value", toLong(row.get("total")));
            ranking.add(item);
        }
        return ranking;
    }

    public long avgResolutionMillis() {
        List<Map<String, Object>> rows = workOrderMapper.selectMaps(new QueryWrapper<WorkOrderRecord>()
                .select("COALESCE(AVG(update_time - create_time), 0) AS avg_millis")
                .in("status", List.of(WorkOrderStatus.RESOLVED.name(), WorkOrderStatus.CLOSED.name())));
        if (rows.isEmpty()) {
            return 0L;
        }
        return toLong(lowerKeys(rows.get(0)).get("avg_millis"));
    }

    private List<Map<String, Object>> distributions(String column) {
        List<Map<String, Object>> rows = workOrderMapper.selectMaps(new QueryWrapper<WorkOrderRecord>()
                .select(column + " AS name", "COUNT(*) AS total")
                .groupBy(column)
                .orderByDesc("total"));
        List<Map<String, Object>> result = new ArrayList<>(rows.size());
        for (Map<String, Object> raw : rows) {
            Map<String, Object> row = lowerKeys(raw);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", String.valueOf(row.get("name")));
            item.put("value", toLong(row.get("total")));
            result.add(item);
        }
        return result;
    }

    private Map<String, Object> lowerKeys(Map<String, Object> row) {
        Map<String, Object> lowered = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            lowered.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
        }
        return lowered;
    }

    private long toLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }
}
