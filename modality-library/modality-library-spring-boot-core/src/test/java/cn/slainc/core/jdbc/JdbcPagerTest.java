package cn.slainc.core.jdbc;

import cn.slainc.core.result.PageResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JdbcPagerTest {

    private DriverManagerDataSource dataSource;

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        dataSource = new DriverManagerDataSource("jdbc:h2:mem:jdbcpager;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS t_pager_sample (id BIGINT PRIMARY KEY, name VARCHAR(64))");
        jdbcTemplate.update("DELETE FROM t_pager_sample");
        for (int i = 1; i <= 25; i++) {
            jdbcTemplate.update("INSERT INTO t_pager_sample VALUES (?, ?)", i, "row-" + i);
        }
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_pager_sample");
    }

    @Test
    void pageReturnsRequestedSliceWithTotal() {
        PageResult<Long> result = JdbcPager.page(jdbcTemplate,
                "SELECT COUNT(*) FROM t_pager_sample",
                "SELECT id FROM t_pager_sample ORDER BY id",
                List.of(), 2, 10, (rs, rowNum) -> rs.getLong(1));
        assertEquals(10, result.records().size());
        assertEquals(25, result.total());
        assertEquals(2, result.current());
        assertEquals(10, result.size());
        assertEquals(11, result.records().get(0));
    }

    @Test
    void pageAppliesWhereArgs() {
        PageResult<Long> result = JdbcPager.page(jdbcTemplate,
                "SELECT COUNT(*) FROM t_pager_sample WHERE id > ?",
                "SELECT id FROM t_pager_sample WHERE id > ? ORDER BY id",
                List.of(20), 1, 10, (rs, rowNum) -> rs.getLong(1));
        assertEquals(5, result.total());
        assertEquals(5, result.records().size());
    }

    @Test
    void normalizeClampsCurrentAndSize() {
        PageResult<Long> result = JdbcPager.page(jdbcTemplate,
                "SELECT COUNT(*) FROM t_pager_sample",
                "SELECT id FROM t_pager_sample ORDER BY id",
                List.of(), 0, 0, (rs, rowNum) -> rs.getLong(1));
        assertEquals(1, result.current());
        assertEquals(JdbcPager.DEFAULT_PAGE_SIZE, result.size());
    }

    @Test
    void sizeIsCappedByMaxPageSize() {
        PageResult<Long> result = JdbcPager.page(jdbcTemplate,
                "SELECT COUNT(*) FROM t_pager_sample",
                "SELECT id FROM t_pager_sample ORDER BY id",
                List.of(), 1, 10000, (rs, rowNum) -> rs.getLong(1));
        assertEquals(JdbcPager.MAX_PAGE_SIZE, result.size());
        assertEquals(25, result.records().size());
    }

    @Test
    void customMaxPageSizeIsHonored() {
        PageResult<Long> result = JdbcPager.page(jdbcTemplate,
                "SELECT COUNT(*) FROM t_pager_sample",
                "SELECT id FROM t_pager_sample ORDER BY id",
                List.of(), 1, 10000, 5, (rs, rowNum) -> rs.getLong(1));
        assertEquals(5, result.size());
        assertEquals(5, result.records().size());
    }

    @Test
    void extremePageNumberDoesNotProduceNegativeOffset() {
        PageResult<Long> result = JdbcPager.page(jdbcTemplate,
                "SELECT COUNT(*) FROM t_pager_sample",
                "SELECT id FROM t_pager_sample ORDER BY id",
                List.of(), Long.MAX_VALUE, 10, (rs, rowNum) -> rs.getLong(1));
        assertEquals(25, result.total());
        assertEquals(0, result.records().size());
    }

    @Test
    void lastPageReturnsRemainingRecords() {
        PageResult<Long> result = JdbcPager.page(jdbcTemplate,
                "SELECT COUNT(*) FROM t_pager_sample",
                "SELECT id FROM t_pager_sample ORDER BY id",
                List.of(), 3, 10, (rs, rowNum) -> rs.getLong(1));
        assertEquals(5, result.records().size());
        assertEquals(3, result.pages());
    }
}
