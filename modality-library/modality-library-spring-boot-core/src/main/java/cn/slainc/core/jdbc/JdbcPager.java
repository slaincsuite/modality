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
        queryArgs.add(offset(currentPage, pageSize));
        List<T> records = jdbcTemplate.query(selectSql + " LIMIT ? OFFSET ?", rowMapper, queryArgs.toArray());
        return PageResult.of(records, total == null ? 0 : total, currentPage, pageSize);
    }

    // 页号接近 Long.MAX_VALUE 时相乘会溢出成负数，偏移量必须饱和而不是回绕
    private static long offset(long currentPage, long pageSize) {
        long pages = currentPage - 1L;
        if (pages <= 0L) {
            return 0L;
        }
        return pages > Long.MAX_VALUE / pageSize ? Long.MAX_VALUE : pages * pageSize;
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
