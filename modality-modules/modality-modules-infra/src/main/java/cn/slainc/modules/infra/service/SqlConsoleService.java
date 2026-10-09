package cn.slainc.modules.infra.service;

import cn.slainc.core.lang.BizAssert;
import cn.slainc.mybatis.sql.SlowSqlRecord;
import cn.slainc.mybatis.sql.SqlAnalyzer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SqlConsoleService {

    private static final Pattern WRITE_KEYWORDS = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|MERGE|REPLACE|CREATE|DROP|ALTER|TRUNCATE|RENAME|GRANT|REVOKE|CALL|EXEC|EXECUTE"
                    + "|SET|COMMIT|ROLLBACK|SAVEPOINT|LOCK|UNLOCK|HANDLER|LOAD|OUTFILE|DUMPFILE)\\b",
            Pattern.CASE_INSENSITIVE);

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
        String statement = sql.trim();
        if (statement.endsWith(";")) {
            statement = statement.substring(0, statement.length() - 1).trim();
        }
        // 校验在剥离注释与字符串字面量后的副本上做，执行仍用原语句以保留 hint 注释
        String scrubbed = stripLiterals(stripComments(statement));
        BizAssert.isTrue(scrubbed.indexOf(';') < 0, "执行计划与索引建议只接受单条只读查询语句");
        String head = scrubbed.substring(0, Math.min(12, scrubbed.length())).toUpperCase(Locale.ROOT);
        BizAssert.isTrue(head.startsWith("SELECT") || head.startsWith("WITH"),
                "执行计划与索引建议只接受 SELECT / WITH 查询语句");
        // 数据修改型 CTE（WITH x AS (DELETE ...) SELECT ...）前缀是 WITH，只能按关键字拦截
        Matcher matcher = WRITE_KEYWORDS.matcher(scrubbed);
        boolean writeKeyword = matcher.find();
        BizAssert.isTrue(!writeKeyword, "只读控制台拒绝数据修改语句，命中关键字："
                + (writeKeyword ? matcher.group(1).toUpperCase(Locale.ROOT) : ""));
        return statement;
    }

    private static String stripComments(String sql) {
        return sql.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)--[^\\n]*", " ");
    }

    private static String stripLiterals(String sql) {
        return sql.replaceAll("'(?:''|[^'])*'", " ").replaceAll("\"(?:\"\"|[^\"])*\"", " ");
    }
}
