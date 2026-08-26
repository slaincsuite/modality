package cn.slainc.tenant.isolation;

import cn.slainc.core.context.TenantContext;
import cn.slainc.core.context.UserContext;
import cn.slainc.mybatis.properties.MybatisProperties;
import cn.slainc.mybatis.tenant.IgnoreTenant;
import cn.slainc.mybatis.tenant.DefaultTenantLineHandler;
import cn.slainc.mybatis.tenant.TenantHelper;
import cn.slainc.mybatis.tenant.TenantIgnoreInterceptor;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantIsolationIntegrationTest {

    private static final String URL =
            "jdbc:h2:mem:modality-tenant-it;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL";

    private static final List<String> EXECUTED_SQL = new CopyOnWriteArrayList<>();

    private static DataSource dataSource;

    private static SqlSessionFactory sqlSessionFactory;

    private SqlSession session;

    private DemoTenantMapper mapper;

    @BeforeAll
    static void prepareDatabase() throws Exception {
        dataSource = H2TestDatabase.dataSource(URL);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS t_demo");
            statement.execute("CREATE TABLE t_demo (id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL,"
                    + " demo_name VARCHAR(64))");
            statement.execute("DROP TABLE IF EXISTS t_demo_shared");
            statement.execute("CREATE TABLE t_demo_shared (id BIGINT PRIMARY KEY, demo_name VARCHAR(64))");
            statement.execute("DROP TABLE IF EXISTS t_demo_write");
            statement.execute("CREATE TABLE t_demo_write (id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL,"
                    + " demo_name VARCHAR(64))");
            statement.execute("INSERT INTO t_demo VALUES (1, 1, 'A-1'), (2, 1, 'A-2'), (3, 2, 'B-1')");
            statement.execute("INSERT INTO t_demo_shared VALUES (1, 'shared')");
        }

        MybatisProperties properties = new MybatisProperties();
        properties.getTenant().setEnabled(true);
        properties.getTenant().setSuperAdminIgnores(true);
        properties.getTenant().setIgnoreTables(new ArrayList<>(List.of("t_tenant", "t_demo_shared")));

        MybatisPlusInterceptor plusInterceptor = new MybatisPlusInterceptor();
        plusInterceptor.setInterceptors(List.of(new TenantLineInnerInterceptor(new DefaultTenantLineHandler(properties))));

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("tenant-it", new JdbcTransactionFactory(), dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addInterceptor(new SqlCaptureInterceptor());
        configuration.addInterceptor(plusInterceptor);
        configuration.addInterceptor(new TenantIgnoreInterceptor());
        configuration.addMapper(DemoTenantMapper.class);
        sqlSessionFactory = new MybatisSqlSessionFactoryBuilder().build(configuration);
    }

    @BeforeEach
    void openSession() throws Exception {
        session = sqlSessionFactory.openSession(true);
        mapper = session.getMapper(DemoTenantMapper.class);
        EXECUTED_SQL.clear();
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM t_demo_write");
        }
    }

    @AfterEach
    void closeSession() {
        session.close();
        TenantContext.clear();
        UserContext.clear();
        while (TenantHelper.isIgnored()) {
            TenantHelper.endIgnore();
        }
    }

    @Test
    @DisplayName("租户 A 上下文中只查询到 A 的数据")
    void tenantASeesOnlyOwnRows() {
        TenantContext.runWith(1L, () -> assertEquals(List.of("A-1", "A-2"), names(mapper.selectAll())));
    }

    @Test
    @DisplayName("切换租户上下文后查询结果随之切换")
    void switchingTenantSwitchesRows() {
        TenantContext.runWith(1L, () -> assertEquals(2, mapper.selectAll().size()));
        TenantContext.runWith(2L, () -> assertEquals(List.of("B-1"), names(mapper.selectAll())));
        assertEquals("B-1", TenantContext.callWith(2L, () -> mapper.selectAll().get(0).getDemoName()));
    }

    @Test
    @DisplayName("缺少上下文时按默认租户 1 过滤")
    void missingContextFallsBackToDefaultTenant() {
        assertEquals(List.of("A-1", "A-2"), names(mapper.selectAll()));
    }

    @Test
    @DisplayName("跨租户写入的数据彼此不可见")
    void crossTenantWritesAreIsolated() {
        TenantContext.runWith(1L, () -> mapper.insertWrite(new DemoRow(101L, 1L, "A-write")));
        TenantContext.runWith(2L, () -> mapper.insertWrite(new DemoRow(102L, 2L, "B-write")));
        TenantContext.runWith(1L, () -> assertEquals(List.of("A-write"), names(mapper.selectWrite())));
        TenantContext.runWith(2L, () -> assertEquals(List.of("B-write"), names(mapper.selectWrite())));
        TenantHelper.ignore(() -> assertEquals(2, mapper.selectWrite().size()));
    }

    @Test
    @DisplayName("白名单表不受租户条件影响")
    void whitelistedTableIgnoresTenant() {
        TenantContext.runWith(1L, () -> assertEquals(1, mapper.selectShared().size()));
        TenantContext.runWith(2L, () -> assertEquals(1, mapper.selectShared().size()));
    }

    @Test
    @DisplayName("超级管理员跨租户查询不受租户条件限制")
    void superAdminSeesAllRows() {
        UserContext.set(new UserContext.UserInfo(1L, "admin", null, Set.of("admin"), Set.of("*"), true));
        assertEquals(3, mapper.selectAll().size());
        UserContext.clear();
        assertEquals(2, mapper.selectAll().size());
    }

    @Test
    @DisplayName("TenantHelper.ignore 可临时关闭租户条件")
    void ignoreTenantTemporarily() {
        TenantContext.runWith(1L, () -> {
            assertEquals(2, mapper.selectAll().size());
            TenantHelper.ignore(() -> assertEquals(3, mapper.selectAll().size()));
            assertEquals(2, mapper.selectAll().size());
        });
    }

    @Test
    @DisplayName("@IgnoreTenant 标注的 Mapper 方法跳过租户条件")
    void ignoreTenantAnnotationBypassesTenantFilter() {
        TenantContext.runWith(1L, () -> assertEquals(3, mapper.selectAllIgnoringTenant().size()));
        assertEquals(3, mapper.selectAllIgnoringTenant().size());
    }

    @Test
    @DisplayName("真实执行到数据库的 SQL 里带上了当前租户的等值条件")
    void executedSqlCarriesTenantPredicate() {
        TenantContext.runWith(1L, () -> mapper.selectAll());
        assertTrue(lastSql().contains("tenant_id = 1"), lastSql());
        TenantContext.runWith(2L, () -> mapper.selectAll());
        assertTrue(lastSql().contains("tenant_id = 2"), lastSql());
        TenantContext.clear();
        mapper.selectAll();
        assertTrue(lastSql().contains("tenant_id = 1"), lastSql());
    }

    @Test
    @DisplayName("白名单表、忽略模式与超级管理员的 SQL 不带租户条件")
    void executedSqlHasNoTenantPredicateWhenBypassed() {
        TenantContext.runWith(1L, () -> mapper.selectShared());
        assertFalse(lastSql().contains("tenant_id = "), lastSql());

        TenantHelper.ignore(() -> mapper.selectAll());
        assertFalse(lastSql().contains("tenant_id = "), lastSql());

        UserContext.set(new UserContext.UserInfo(1L, "admin", null, Set.of("admin"), Set.of("*"), true));
        mapper.selectAll();
        UserContext.clear();
        assertFalse(lastSql().contains("tenant_id = "), lastSql());

        TenantContext.runWith(1L, () -> mapper.selectAllIgnoringTenant());
        assertFalse(lastSql().contains("tenant_id = "), lastSql());
    }

    private static String lastSql() {
        return EXECUTED_SQL.get(EXECUTED_SQL.size() - 1);
    }

    private static List<String> names(List<DemoRow> rows) {
        return rows.stream().map(DemoRow::getDemoName).toList();
    }

    @Intercepts({@Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class,
            RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class})})
    public static class SqlCaptureInterceptor implements Interceptor {

        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            EXECUTED_SQL.add(((BoundSql) invocation.getArgs()[5]).getSql());
            return invocation.proceed();
        }
    }

    interface DemoTenantMapper {

        @Select("SELECT id, tenant_id, demo_name FROM t_demo ORDER BY id")
        List<DemoRow> selectAll();

        @IgnoreTenant
        @Select("SELECT id, tenant_id, demo_name FROM t_demo ORDER BY id")
        List<DemoRow> selectAllIgnoringTenant();

        @Select("SELECT id, demo_name FROM t_demo_shared ORDER BY id")
        List<SharedRow> selectShared();

        @Insert("INSERT INTO t_demo_write (id, tenant_id, demo_name) VALUES (#{id}, #{tenantId}, #{demoName})")
        int insertWrite(DemoRow row);

        @Select("SELECT id, tenant_id, demo_name FROM t_demo_write ORDER BY id")
        List<DemoRow> selectWrite();
    }

    public static class DemoRow {

        private Long id;

        private Long tenantId;

        private String demoName;

        public DemoRow() {
        }

        public DemoRow(Long id, Long tenantId, String demoName) {
            this.id = id;
            this.tenantId = tenantId;
            this.demoName = demoName;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Long getTenantId() {
            return tenantId;
        }

        public void setTenantId(Long tenantId) {
            this.tenantId = tenantId;
        }

        public String getDemoName() {
            return demoName;
        }

        public void setDemoName(String demoName) {
            this.demoName = demoName;
        }
    }

    public static class SharedRow {

        private Long id;

        private String demoName;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getDemoName() {
            return demoName;
        }

        public void setDemoName(String demoName) {
            this.demoName = demoName;
        }
    }
}
