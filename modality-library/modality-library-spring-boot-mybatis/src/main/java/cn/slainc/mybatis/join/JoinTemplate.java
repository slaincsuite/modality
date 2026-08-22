package cn.slainc.mybatis.join;

import cn.slainc.core.result.PageResult;
import cn.slainc.datasource.enums.DatabaseType;
import cn.slainc.mybatis.page.PageQuery;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class JoinTemplate {

    private final JdbcTemplate jdbcTemplate;

    public JoinTemplate(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> selectMaps(JoinQuery query) {
        applyPaging(query, DatabaseType.POSTGRE_SQL);
        return jdbcTemplate.queryForList(query.sql(), query.parameters().toArray());
    }

    public Map<String, Object> selectOne(JoinQuery query) {
        List<Map<String, Object>> rows = selectMaps(query);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public <T> List<T> selectList(JoinQuery query, Class<T> type) {
        return selectList(query, new BeanPropertyRowMapper<>(type));
    }

    public <T> List<T> selectList(JoinQuery query, RowMapper<T> rowMapper) {
        applyPaging(query, DatabaseType.POSTGRE_SQL);
        return jdbcTemplate.query(query.sql(), rowMapper, query.parameters().toArray());
    }

    public <T> T selectOne(JoinQuery query, Class<T> type) {
        List<T> rows = selectList(query, type);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public <T> PageResult<T> selectPage(JoinQuery query, Class<T> type, PageQuery page) {
        return selectPage(query, type, page, DatabaseType.POSTGRE_SQL);
    }

    public <T> PageResult<T> selectPage(JoinQuery query, Class<T> type, PageQuery page, DatabaseType databaseType) {
        Long total = jdbcTemplate.queryForObject(query.countSql(), Long.class, query.parameters().toArray());
        long totalValue = total == null ? 0L : total;
        PageQuery effective = page == null ? new PageQuery() : page;
        long pageNum = effective.normalizedPageNum();
        long pageSize = effective.normalizedPageSize();
        if (totalValue == 0) {
            return PageResult.of(List.of(), 0L, pageNum, pageSize);
        }
        query.offset((int) ((pageNum - 1) * pageSize)).limit((int) pageSize);
        applyPaging(query, databaseType);
        List<T> records = jdbcTemplate.query(query.sql(), new BeanPropertyRowMapper<>(type),
                query.parameters().toArray());
        return PageResult.of(records, totalValue, pageNum, pageSize);
    }

    public <P, C> List<P> groupChildren(List<P> parents, Function<P, Object> parentKey,
                                        List<C> children, Function<C, Object> childKey,
                                        BiConsumer<P, List<C>> setter) {
        Map<Object, List<C>> grouped = new LinkedHashMap<>();
        if (children != null) {
            for (C child : children) {
                grouped.computeIfAbsent(childKey.apply(child), key -> new ArrayList<>()).add(child);
            }
        }
        if (parents != null) {
            for (P parent : parents) {
                setter.accept(parent, grouped.getOrDefault(parentKey.apply(parent), new ArrayList<>()));
            }
        }
        return parents;
    }

    private static void applyPaging(JoinQuery query, DatabaseType databaseType) {
        if (query.getLimit() == null) {
            return;
        }
        int limit = query.getLimit();
        int offset = query.getOffset() == null ? 0 : query.getOffset();
        boolean fetchFirst = databaseType == DatabaseType.ORACLE || databaseType == DatabaseType.SQL_SERVER
                || databaseType == DatabaseType.DM;
        query.paging(fetchFirst
                ? " OFFSET " + offset + " ROWS FETCH NEXT " + limit + " ROWS ONLY"
                : " LIMIT " + limit + " OFFSET " + offset);
    }
}
