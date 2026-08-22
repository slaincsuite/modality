package cn.slainc.mybatis.join;

import cn.slainc.mybatis.exception.MybatisException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.StringJoiner;

public final class JoinQuery {

    private final String from;

    private final List<String> selects = new ArrayList<>();

    private final List<String> joins = new ArrayList<>();

    private final List<String> conditions = new ArrayList<>();

    private final List<Object> parameters = new ArrayList<>();

    private final List<String> groupBy = new ArrayList<>();

    private final List<String> having = new ArrayList<>();

    private final List<String> orderBy = new ArrayList<>();

    private Integer limit;

    private Integer offset;

    private String suffix = "";

    private JoinQuery(String from) {
        if (from == null || from.isBlank()) {
            throw new MybatisException("查询主表不能为空");
        }
        this.from = from;
    }

    public static JoinQuery from(String table) {
        return new JoinQuery(table);
    }

    public JoinQuery select(String... columns) {
        for (String column : columns) {
            selects.add(column);
        }
        return this;
    }

    public JoinQuery innerJoin(String table, String on) {
        joins.add(" INNER JOIN " + table + " ON " + on);
        return this;
    }

    public JoinQuery leftJoin(String table, String on) {
        joins.add(" LEFT JOIN " + table + " ON " + on);
        return this;
    }

    public JoinQuery rightJoin(String table, String on) {
        joins.add(" RIGHT JOIN " + table + " ON " + on);
        return this;
    }

    public JoinQuery subQueryJoin(String alias, JoinQuery subQuery, String on) {
        joins.add(" JOIN (" + subQuery.sql() + ") " + alias + " ON " + on);
        parameters.addAll(subQuery.parameters());
        return this;
    }

    public JoinQuery subQueryIn(String column, JoinQuery subQuery) {
        conditions.add(column + " IN (" + subQuery.sql() + ")");
        parameters.addAll(subQuery.parameters());
        return this;
    }

    public JoinQuery where(String condition, Object... params) {
        conditions.add(condition);
        addParameters(params);
        return this;
    }

    public JoinQuery and(String condition, Object... params) {
        return where(condition, params);
    }

    public JoinQuery in(String column, Collection<?> values) {
        if (values == null || values.isEmpty()) {
            conditions.add("1 = 0");
            return this;
        }
        StringJoiner joiner = new StringJoiner(", ", column + " IN (", ")");
        for (Object value : values) {
            joiner.add("?");
            parameters.add(value);
        }
        conditions.add(joiner.toString());
        return this;
    }

    public JoinQuery groupBy(String... columns) {
        for (String column : columns) {
            groupBy.add(column);
        }
        return this;
    }

    public JoinQuery having(String condition, Object... params) {
        having.add(condition);
        addParameters(params);
        return this;
    }

    public JoinQuery orderBy(String... columns) {
        for (String column : columns) {
            orderBy.add(column);
        }
        return this;
    }

    public JoinQuery limit(int limit) {
        this.limit = limit;
        return this;
    }

    public JoinQuery offset(int offset) {
        this.offset = offset;
        return this;
    }

    public JoinQuery paging(String clause) {
        this.suffix = clause == null ? "" : clause;
        return this;
    }

    public List<Object> parameters() {
        return List.copyOf(parameters);
    }

    public String sql() {
        return render(true);
    }

    public String countSql() {
        return "SELECT COUNT(*) FROM (" + render(false) + ") modality_count";
    }

    private String render(boolean withOrderAndLimit) {
        StringBuilder builder = new StringBuilder("SELECT ");
        builder.append(selects.isEmpty() ? "*" : String.join(", ", selects));
        builder.append(" FROM ").append(from);
        joins.forEach(builder::append);
        if (!conditions.isEmpty()) {
            builder.append(" WHERE ").append(String.join(" AND ", conditions));
        }
        if (!groupBy.isEmpty()) {
            builder.append(" GROUP BY ").append(String.join(", ", groupBy));
        }
        if (!having.isEmpty()) {
            builder.append(" HAVING ").append(String.join(" AND ", having));
        }
        if (withOrderAndLimit && !orderBy.isEmpty()) {
            builder.append(" ORDER BY ").append(String.join(", ", orderBy));
        }
        if (withOrderAndLimit) {
            builder.append(suffix);
        }
        return builder.toString();
    }

    private void addParameters(Object[] params) {
        if (params == null) {
            return;
        }
        for (Object param : params) {
            parameters.add(param);
        }
    }

    public Integer getLimit() {
        return limit;
    }

    public Integer getOffset() {
        return offset;
    }
}
