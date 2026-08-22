package cn.slainc.mybatis.sql;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SqlAnalyzer {

    private final JdbcTemplate jdbcTemplate;

    private final SqlStatistics statistics;

    public SqlAnalyzer(JdbcTemplate jdbcTemplate, SqlStatistics statistics) {
        this.jdbcTemplate = jdbcTemplate;
        this.statistics = statistics;
    }

    public List<Map<String, Object>> explain(String sql) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("EXPLAIN " + sql);
        List<Map<String, Object>> normalized = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            row.forEach((key, value) -> item.put(key.toLowerCase(Locale.ROOT), value));
            normalized.add(item);
        }
        return normalized;
    }

    public List<String> suggestIndexes(String sql) {
        List<String> suggestions = new ArrayList<>();
        List<Map<String, Object>> rows;
        try {
            rows = explain(sql);
        } catch (RuntimeException exception) {
            return List.of("无法获取执行计划: " + exception.getMessage());
        }
        for (Map<String, Object> row : rows) {
            Object type = row.get("type");
            Object key = row.get("key");
            Object table = row.get("table");
            Object rowsScanned = row.get("rows");
            if (type != null && "ALL".equalsIgnoreCase(String.valueOf(type))) {
                suggestions.add("表 " + table + " 进行了全表扫描，建议为过滤条件列建立索引（预估扫描 "
                        + rowsScanned + " 行）");
            } else if (key == null && table != null) {
                suggestions.add("表 " + table + " 未命中索引，建议检查 WHERE 与 ORDER BY 的列");
            }
            Object extra = row.get("extra");
            if (extra != null && String.valueOf(extra).toLowerCase(Locale.ROOT).contains("filesort")) {
                suggestions.add("表 " + table + " 出现文件排序，建议为 ORDER BY 列建立索引");
            }
        }
        if (suggestions.isEmpty()) {
            suggestions.add("执行计划未发现明显的索引问题");
        }
        return suggestions;
    }

    public List<SlowSqlRecord> topSlow() {
        return statistics.topSlow();
    }

    public Map<String, Long> countByType() {
        return statistics.countByType();
    }

    public Map<String, Long> averageElapsedByType() {
        return statistics.averageElapsedByType();
    }

    public long total() {
        return statistics.total();
    }

    public void reset() {
        statistics.reset();
    }
}
