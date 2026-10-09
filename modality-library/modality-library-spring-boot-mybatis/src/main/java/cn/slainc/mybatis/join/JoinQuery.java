package cn.slainc.mybatis.join;

import cn.slainc.mybatis.exception.MybatisException;
import cn.slainc.mybatis.support.LikeUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.StringJoiner;

public final class JoinQuery {

    private final String from;

    private final List<String> selects = new ArrayList<>();

    private final List<Fragment> joins = new ArrayList<>();

    private final List<Fragment> conditions = new ArrayList<>();

    private final List<Fragment> having = new ArrayList<>();

    private final List<String> groupBy = new ArrayList<>();

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
        joins.add(new Fragment(" INNER JOIN " + table + " ON " + on, List.of()));
        return this;
    }

    public JoinQuery leftJoin(String table, String on) {
        joins.add(new Fragment(" LEFT JOIN " + table + " ON " + on, List.of()));
        return this;
    }

    public JoinQuery rightJoin(String table, String on) {
        joins.add(new Fragment(" RIGHT JOIN " + table + " ON " + on, List.of()));
        return this;
    }

    public JoinQuery subQueryJoin(String alias, JoinQuery subQuery, String on) {
        joins.add(new Fragment(" JOIN (" + subQuery.sql() + ") " + alias + " ON " + on, subQuery.parameters()));
        return this;
    }

    public JoinQuery subQueryIn(String column, JoinQuery subQuery) {
        conditions.add(new Fragment(column + " IN (" + subQuery.sql() + ")", subQuery.parameters()));
        return this;
    }

    public JoinQuery where(String condition, Object... params) {
        conditions.add(new Fragment(condition, values(params)));
        return this;
    }

    public JoinQuery and(String condition, Object... params) {
        return where(condition, params);
    }

    public JoinQuery in(String column, Collection<?> values) {
        if (values == null || values.isEmpty()) {
            conditions.add(new Fragment("1 = 0", List.of()));
            return this;
        }
        StringJoiner joiner = new StringJoiner(", ", column + " IN (", ")");
        List<Object> params = new ArrayList<>();
        for (Object value : values) {
            joiner.add("?");
            params.add(value);
        }
        conditions.add(new Fragment(joiner.toString(), params));
        return this;
    }

    public JoinQuery contains(String column, String value) {
        return like(column, LikeUtils.contains(value));
    }

    public JoinQuery startsWith(String column, String value) {
        return like(column, LikeUtils.startsWith(value));
    }

    public JoinQuery endsWith(String column, String value) {
        return like(column, LikeUtils.endsWith(value));
    }

    public JoinQuery groupBy(String... columns) {
        for (String column : columns) {
            groupBy.add(column);
        }
        return this;
    }

    public JoinQuery having(String condition, Object... params) {
        having.add(new Fragment(condition, values(params)));
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
        List<Object> all = new ArrayList<>();
        collect(all, joins);
        collect(all, conditions);
        collect(all, having);
        return List.copyOf(all);
    }

    public String sql() {
        return render(true);
    }

    public String countSql() {
        return "SELECT COUNT(*) FROM (" + render(false) + ") join_count";
    }

    private String render(boolean withOrderAndLimit) {
        StringBuilder builder = new StringBuilder("SELECT ");
        builder.append(selects.isEmpty() ? "*" : String.join(", ", selects));
        builder.append(" FROM ").append(from);
        joins.forEach(fragment -> builder.append(fragment.sql()));
        if (!conditions.isEmpty()) {
            builder.append(" WHERE ").append(joinSql(conditions));
        }
        if (!groupBy.isEmpty()) {
            builder.append(" GROUP BY ").append(String.join(", ", groupBy));
        }
        if (!having.isEmpty()) {
            builder.append(" HAVING ").append(joinSql(having));
        }
        if (withOrderAndLimit && !orderBy.isEmpty()) {
            builder.append(" ORDER BY ").append(String.join(", ", orderBy));
        }
        if (withOrderAndLimit) {
            builder.append(suffix);
        }
        return builder.toString();
    }

    private JoinQuery like(String column, String pattern) {
        if (pattern == null) {
            throw new MybatisException("LIKE 匹配值不能为空");
        }
        conditions.add(new Fragment(column + " LIKE ?" + LikeUtils.escapeClause(), List.of(pattern)));
        return this;
    }

    private static void collect(List<Object> target, List<Fragment> fragments) {
        for (Fragment fragment : fragments) {
            target.addAll(fragment.parameters());
        }
    }

    private static String joinSql(List<Fragment> fragments) {
        StringJoiner joiner = new StringJoiner(" AND ");
        for (Fragment fragment : fragments) {
            joiner.add(fragment.sql());
        }
        return joiner.toString();
    }

    private static List<Object> values(Object[] params) {
        List<Object> values = new ArrayList<>();
        if (params != null) {
            for (Object param : params) {
                values.add(param);
            }
        }
        return values;
    }

    public Integer getLimit() {
        return limit;
    }

    public Integer getOffset() {
        return offset;
    }

    // 参数随片段一起记录，渲染顺序（joins → where → having）与绑定顺序才不会错位
    private record Fragment(String sql, List<Object> parameters) {
    }
}
