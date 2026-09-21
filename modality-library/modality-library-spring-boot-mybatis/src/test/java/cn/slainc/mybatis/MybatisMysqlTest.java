package cn.slainc.mybatis;

import cn.slainc.core.context.TenantContext;
import cn.slainc.core.context.UserContext;
import cn.slainc.mybatis.entity.User;
import cn.slainc.mybatis.mapper.UserMapper;
import cn.slainc.mybatis.sql.SqlAnalyzer;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIf("cn.slainc.mybatis.MySqlAvailability#available")
@SpringBootTest(classes = MybatisTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.datasource.dynamic.datasource.master.driver-class-name=com.mysql.cj.jdbc.Driver",
                "spring.datasource.dynamic.datasource.master.url=" + MySqlAvailability.URL,
                "spring.datasource.dynamic.datasource.master.username=" + MySqlAvailability.USER,
                "spring.datasource.dynamic.datasource.master.password=" + MySqlAvailability.PASSWORD
        })
class MybatisMysqlTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private SqlAnalyzer sqlAnalyzer;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS t_user ("
                + " id BIGINT PRIMARY KEY, tenant_id BIGINT, name VARCHAR(64), phone VARCHAR(32),"
                + " secret VARCHAR(512), remark JSON, dept_id BIGINT, created_at DATETIME, updated_at DATETIME,"
                + " created_by VARCHAR(64), updated_by VARCHAR(64), deleted INT DEFAULT 0, version INT DEFAULT 0)");
        jdbcTemplate.execute("DELETE FROM t_user");
        TenantContext.clear();
        UserContext.clear();
        TenantContext.set(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        UserContext.clear();
    }

    @Test
    @DisplayName("MySQL 上物理分页走 LIMIT 方言且总数正确")
    void paginationUsesMysqlDialect() {
        for (int i = 1; i <= 23; i++) {
            insertUser(String.format("mysql-%02d", i), 1L);
        }
        Page<User> page = userMapper.selectPage(new Page<>(2, 10), new QueryWrapper<User>().orderByAsc("name"));
        assertEquals(23L, page.getTotal());
        assertEquals(10, page.getRecords().size());
        assertEquals("mysql-11", page.getRecords().get(0).getName());
        assertEquals(3L, page.getPages());
    }

    @Test
    @DisplayName("MySQL 上跨租户查询同样被拦截")
    void tenantIsolationOnMysql() {
        insertUser("mysql-tenant-1", 1L);
        insertUser("mysql-tenant-2", 2L);

        TenantContext.set(1L);
        List<User> tenantOne = userMapper.selectList(null);
        assertEquals(1, tenantOne.size());
        assertEquals(1L, tenantOne.get(0).getTenantId());

        TenantContext.set(2L);
        assertEquals(2L, userMapper.selectList(null).get(0).getTenantId());

        UserContext.set(new UserContext.UserInfo(1L, "admin", cn.slainc.core.enums.LoginType.PLATFORM_ADMIN,
                java.util.Set.of(), java.util.Set.of(), true));
        assertEquals(2, userMapper.selectList(null).size());
    }

    @Test
    @DisplayName("MySQL 上慢 SQL 统计与执行计划可用")
    void slowSqlAndExplainOnMysql() {
        sqlAnalyzer.reset();
        insertUser("mysql-slow", 1L);
        userMapper.selectList(null);
        assertFalse(sqlAnalyzer.topSlow().isEmpty());
        assertTrue(sqlAnalyzer.countByType().getOrDefault("SELECT", 0L) >= 1);

        List<String> suggestions = sqlAnalyzer.suggestIndexes("SELECT * FROM t_user WHERE name = 'mysql-slow'");
        assertFalse(suggestions.isEmpty());
    }

    private void insertUser(String name, Long tenantId) {
        User user = new User();
        user.setId((long) (Math.abs(name.hashCode()) % 100000) + 1L);
        user.setName(name);
        user.setTenantId(tenantId);
        userMapper.insert(user);
    }
}
