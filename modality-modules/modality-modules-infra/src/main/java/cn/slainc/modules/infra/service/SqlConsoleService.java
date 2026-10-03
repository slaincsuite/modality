package cn.slainc.modules.infra.service;

import cn.slainc.core.lang.BizAssert;
import cn.slainc.mybatis.sql.SlowSqlRecord;
import cn.slainc.mybatis.sql.SqlAnalyzer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SqlConsoleService {

    private final SqlAnalyzer sqlAnalyzer;

    public SqlConsoleService(SqlAnalyzer sqlAnalyzer) {
        this.sqlAnalyzer = sqlAnalyzer;
    }

    public List<SlowSqlRecord> topSlow() {
        return sqlAnalyzer.topSlow();
    }

    public Map<String, Object> stats() {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("total", sqlAnalyzer.total());
        view.put("countByType", sqlAnalyzer.countByType());
        view.put("averageElapsedMillisByType", sqlAnalyzer.averageElapsedByType());
        return view;
    }

    public List<Map<String, Object>> explain(String sql) {
        return sqlAnalyzer.explain(requireReadOnly(sql));
    }

    public List<String> indexSuggestions(String sql) {
        return sqlAnalyzer.suggestIndexes(requireReadOnly(sql));
    }

    private static String requireReadOnly(String sql) {
        BizAssert.hasText(sql, "SQL 语句不能为空");
        String trimmed = sql.trim();
        String withoutComments = trimmed.replaceFirst("(?i)^--[^\\n]*\\n", "");
        String head = withoutComments.substring(0, Math.min(12, withoutComments.length())).toUpperCase();
        BizAssert.isTrue(head.startsWith("SELECT") || head.startsWith("WITH"),
                "执行计划与索引建议只接受 SELECT / WITH 查询语句");
        return trimmed.endsWith(";") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
