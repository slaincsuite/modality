package cn.slainc.datasource;

import cn.slainc.datasource.definition.DataSourceDefinition;
import cn.slainc.datasource.enums.DataSourceStatus;
import cn.slainc.datasource.enums.DatabaseType;
import cn.slainc.datasource.enums.PoolType;
import cn.slainc.datasource.manager.DynamicDataSourceManager;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = DataSourceTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "modality.datasource.pool-type=DRUID")
class DruidPoolIntegrationTest {

    private static final String DRUID_CLASS = "com.alibaba.druid.pool.DruidDataSource";

    private static final String DRUID_URL = "jdbc:h2:mem:modality-druid;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private DynamicDataSourceManager manager;

    @BeforeEach
    void setUp() {
        ProbeSql.bind(dataSource);
        ProbeSql.prepare(DRUID_URL, "druid-pool");
        removeRegistered();
    }

    @AfterEach
    void tearDown() {
        removeRegistered();
    }

    private void removeRegistered() {
        if (manager.exists("druid_report")) {
            manager.remove("druid_report");
        }
    }

    @Test
    @DisplayName("默认连接池按配置切换为 Druid")
    void defaultPoolIsDruid() {
        assertEquals(DRUID_CLASS, unwrap(manager.dataSources().get("master")).getClass().getName());
    }

    @Test
    @DisplayName("运行时注册的数据源也使用 Druid 并能立即读写")
    void runtimeRegisteredDataSourceUsesDruid() {
        DataSourceDefinition definition = new DataSourceDefinition();
        definition.setName("druid_report");
        definition.setDatabaseType(DatabaseType.H2);
        definition.setPoolType(PoolType.DRUID);
        definition.setUrl(DRUID_URL);
        definition.setUsername("sa");
        definition.setPassword("");
        definition.setStatus(DataSourceStatus.ENABLED);
        manager.register(definition);

        assertEquals(DRUID_CLASS, unwrap(manager.dataSources().get("druid_report")).getClass().getName());
        DynamicDataSourceContextHolder.push("druid_report");
        try {
            assertEquals("druid-pool", ProbeSql.label());
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }

    @Test
    @DisplayName("Druid 连接池参数生效且能真实查询")
    void druidPoolOptionsApply() {
        if (!manager.exists("druid_report")) {
            DataSourceDefinition definition = new DataSourceDefinition();
            definition.setName("druid_report");
            definition.setDatabaseType(DatabaseType.H2);
            definition.setPoolType(PoolType.DRUID);
            definition.setUrl(DRUID_URL);
            definition.setUsername("sa");
            definition.setPassword("");
            definition.setStatus(DataSourceStatus.ENABLED);
            manager.register(definition);
        }
        DataSource pool = unwrap(manager.dataSources().get("master"));
        Object maxActive = invoke(pool, "getMaxActive");
        assertTrue(maxActive instanceof Integer, "Druid 应能读取到最大连接数");
        Object minIdle = invoke(pool, "getMinIdle");
        assertTrue(minIdle instanceof Integer, "Druid 应能读取到最小空闲连接数");

        DynamicDataSourceContextHolder.push("druid_report");
        try {
            assertEquals("druid-pool", ProbeSql.label());
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }

    private static DataSource unwrap(DataSource dataSource) {
        if (dataSource instanceof com.baomidou.dynamic.datasource.ds.ItemDataSource item) {
            return item.getRealDataSource();
        }
        return dataSource;
    }

    private static Object invoke(Object target, String methodName) {
        try {
            return target.getClass().getMethod(methodName).invoke(target);
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }
}
