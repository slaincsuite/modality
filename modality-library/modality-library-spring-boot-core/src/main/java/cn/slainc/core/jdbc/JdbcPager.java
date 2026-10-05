package cn.slainc.core.jdbc;

import cn.slainc.core.result.PageResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.ArrayList;
import java.util.List;

public final class JdbcPager {

    public static final long DEFAULT_PAGE_SIZE = 10;

    public static final long MAX_PAGE_SIZE = 200;

    private JdbcPager() {
    }

    public static <T> PageResult<T> page(JdbcTemplate jdbcTemplate, String countSql, String selectSql,
            List<Object> args, long current, long size, RowMapper<T> rowMapper) {
        return page(jdbcTemplate, countSql, selectSql, args, current, size, MAX_PAGE_SIZE, rowMapper);
    }

    public static <T> PageResult<T> page(JdbcTemplate jdbcTemplate, String countSql, String selectSql,
            List<Object> args, long current, long size, long maxPageSize, RowMapper<T> rowMapper) {
        long currentPage = normalizeCurrent(current);
        long pageSize = normalizeSize(size, maxPageSize);
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, args.toArray());
        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(pageSize);
        queryArgs.add((currentPage - 1) * pageSize);
        List<T> records = jdbcTemplate.query(selectSql + " LIMIT ? OFFSET ?", rowMapper, queryArgs.toArray());
        return PageResult.of(records, total == null ? 0 : total, currentPage, pageSize);
    }

    public static long normalizeCurrent(long current) {
        return Math.max(current, 1);
    }

    public static long normalizeSize(long size, long maxPageSize) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, Math.max(1, maxPageSize));
    }
}
