package cn.slainc.datasource;

import cn.slainc.datasource.definition.DataSourceDefinition;
import cn.slainc.datasource.enums.DataSourceStatus;
import cn.slainc.datasource.manager.DataSourceHealth;
import cn.slainc.datasource.manager.DynamicDataSourceManager;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = DataSourceTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DynamicDataSourceManagerIntegrationTest {

    private static final String REPORT_URL = "jdbc:h2:mem:modality-ds-report;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL";

    private static final String REPORT_V2_URL = "jdbc:h2:mem:modality-ds-report-v2;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL";

    private static final String SECURE_URL = "jdbc:h2:mem:modality-ds-secure;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL";

    private static final String SECURE_PASSWORD = "s3cret-pass";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private DynamicDataSourceManager manager;

    @Autowired
    private DataSourceTestApplication.RoutingProbe routingProbe;

    @Autowired
    private DataSourceTestApplication.TransactionProbe transactionProbe;

    @Autowired
    private cn.slainc.datasource.migration.DataSourceMigrationTool migrationTool;

    @Autowired
    private cn.slainc.datasource.transaction.TransactionHelper transactionHelper;

    @BeforeEach
    void setUp() {
        ProbeSql.bind(dataSource);
        ProbeSql.prepare("jdbc:h2:mem:modality-ds;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL", "master");
        ProbeSql.prepare("jdbc:h2:mem:modality-ds-slave1;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL", "slave_1");
        ProbeSql.prepare("jdbc:h2:mem:modality-ds-slave2;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL", "slave_2");
        ProbeSql.prepare(REPORT_URL, "report");
        ProbeSql.prepare(REPORT_V2_URL, "report-v2");
        ProbeSql.prepare(SECURE_URL, "secure", SECURE_PASSWORD);
    }

    @AfterEach
    void tearDown() {
        manager.setPrimary("master");
        for (String name : Set.copyOf(manager.dataSourceNames())) {
            if (name.equals(manager.primaryName())) {
                continue;
            }
            try {
                manager.remove(name);
            } catch (RuntimeException ignored) {
                // YAML 定义的数据源不归管理器所有，跳过即可
            }
        }
    }

    @Test
    @DisplayName("默认数据源可正常访问")
    void defaultDataSourceIsUsable() {
        assertEquals("master", ProbeSql.label());
        assertEquals("master", manager.primaryName());
    }

    @Test
    @DisplayName("运行时新增数据源可立即使用")
    void registerAtRuntimeIsImmediatelyUsable() {
        assertFalse(manager.exists("report"));

        DataSourceDefinition definition = reportDefinition("report", REPORT_URL);
        manager.register(definition);

        assertTrue(manager.exists("report"));
        assertTrue(manager.dataSourceNames().contains("report"));
        assertEquals("report", routingProbe.report());

        DynamicDataSourceContextHolder.push("report");
        try {
            assertEquals("report", ProbeSql.label());
        } finally {
            DynamicDataSourceContextHolder.poll();
        }

        assertEquals("master", ProbeSql.label());
    }

    @Test
    @DisplayName("运行时修改数据源立即生效")
    void updateAtRuntimeTakesEffectImmediately() {
        manager.register(reportDefinition("report", REPORT_URL));
        assertEquals("report", routingProbe.report());

        DataSourceDefinition updated = reportDefinition("report", REPORT_V2_URL);
        manager.update(updated);

        assertEquals("report-v2", routingProbe.report());
    }

    @Test
    @DisplayName("运行时移除数据源立即生效")
    void removeAtRuntimeTakesEffectImmediately() {
        manager.register(reportDefinition("report", REPORT_URL));
        assertTrue(manager.exists("report"));

        manager.remove("report");

        assertFalse(manager.exists("report"));
        assertTrue(manager.definitions().stream().noneMatch(item -> "report".equals(item.getName())));
    }

    @Test
    @DisplayName("主数据源不可移除")
    void primaryDataSourceCannotBeRemoved() {
        manager.register(reportDefinition("report", REPORT_URL));
        manager.setPrimary("report");
        assertEquals("report", manager.primaryName());
        assertThrows(RuntimeException.class, () -> manager.remove("report"));
    }

    @Test
    @DisplayName("重复注册同名数据源被拒绝")
    void duplicateRegistrationRejected() {
        manager.register(reportDefinition("report", REPORT_URL));
        assertThrows(RuntimeException.class, () -> manager.register(reportDefinition("report", REPORT_URL)));
    }

    @Test
    @DisplayName("@DS 支持 SpEL 表达式动态决定数据源")
    void spelExpressionRoutingWorks() {
        manager.register(reportDefinition("report", REPORT_URL));
        assertEquals("report", routingProbe.byExpression("report"));
        assertEquals("master", routingProbe.byExpression("master"));
        assertEquals("slave_1", routingProbe.byExpression("slave_1"));
    }

    @Test
    @DisplayName("从库分组按轮询与随机策略负载均衡")
    void slaveGroupLoadBalances() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 40; i++) {
            seen.add(routingProbe.slave());
        }
        assertTrue(seen.contains("slave_1"), "从库分组应包含 slave_1，实际: " + seen);
        assertTrue(seen.contains("slave_2"), "从库分组应包含 slave_2，实际: " + seen);
    }

    @Test
    @DisplayName("事务内读操作走主库")
    void readInsideTransactionUsesMaster() {
        assertEquals("master", transactionProbe.readSlaveInsideTransaction());
    }

    @Test
    @DisplayName("显式 @DS 优先于事务强制主库")
    void explicitDataSourceWinsOverTransaction() {
        String label = routingProbe.slaveInTransaction();
        assertNotEquals("master", label);
        assertTrue(label.startsWith("slave_"), "实际: " + label);
    }

    @Test
    @DisplayName("数据源密码以密文落库并可解密后成功连接")
    void passwordIsEncryptedAtRest() {
        DataSourceDefinition definition = reportDefinition("secure", SECURE_URL);
        definition.setPassword(SECURE_PASSWORD);
        manager.register(definition);

        String stored = manager.findDefinition("secure").orElseThrow().getPassword();
        assertTrue(stored.startsWith("ENC("), "落库密码应为密文，实际: " + stored);
        assertFalse(stored.contains(SECURE_PASSWORD));
        assertEquals(SECURE_PASSWORD, manager.toProperty(definition).getPassword());

        DynamicDataSourceContextHolder.push("secure");
        try {
            assertEquals("secure", ProbeSql.label());
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }

    @Test
    @DisplayName("注册失败不会留下孤儿记录")
    void failedRegistrationLeavesNoOrphanRow() {
        DataSourceDefinition broken = reportDefinition("broken", "jdbc:h2:mem:missing;IFEXISTS=TRUE");
        assertThrows(RuntimeException.class, () -> manager.register(broken));
        assertTrue(manager.findDefinition("broken").isEmpty(), "注册失败后不应残留配置记录");

        manager.register(reportDefinition("broken", REPORT_URL));
        assertTrue(manager.exists("broken"));
    }

    @Test
    @DisplayName("数据源健康检查可探测连通性")
    void healthCheckDetectsConnectivity() {
        DataSourceHealth healthy = manager.checkConnection(reportDefinition("report", REPORT_URL));
        assertTrue(healthy.healthy(), "实际: " + healthy.message());
        assertEquals(DataSourceStatus.ENABLED, healthy.status());

        DataSourceDefinition broken = reportDefinition("broken", "jdbc:h2:mem:missing;IFEXISTS=TRUE");
        DataSourceHealth unhealthy = manager.checkConnection(broken);
        assertFalse(unhealthy.healthy());
    }

    @Test
    @DisplayName("数据迁移工具可在数据源之间复制表数据")
    void migrationToolCopiesTableData() {
        manager.register(reportDefinition("report", REPORT_URL));
        ProbeSql.execute("CREATE TABLE IF NOT EXISTS migrate_demo (id INT, name VARCHAR(32))");
        ProbeSql.execute("DELETE FROM migrate_demo");
        ProbeSql.execute("INSERT INTO migrate_demo VALUES (1, 'a')");
        ProbeSql.execute("INSERT INTO migrate_demo VALUES (2, 'b')");
        ProbeSql.execute("INSERT INTO migrate_demo VALUES (3, 'c')");

        DynamicDataSourceContextHolder.push("report");
        try {
            ProbeSql.execute("CREATE TABLE IF NOT EXISTS migrate_demo (id INT, name VARCHAR(32))");
            ProbeSql.execute("DELETE FROM migrate_demo");
        } finally {
            DynamicDataSourceContextHolder.poll();
        }

        int migrated = migrationTool.migrate("migrate_demo", "master", "report", 2);
        assertEquals(3, migrated);

        DynamicDataSourceContextHolder.push("report");
        try {
            assertEquals(3, ProbeSql.queryInt("SELECT COUNT(*) FROM migrate_demo"));
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }

    @Test
    @DisplayName("编程式事务可在指定数据源上提交与回滚")
    void programmaticTransactionOnNamedDataSource() {
        manager.register(reportDefinition("report", REPORT_URL));

        String label = transactionHelper.executeOn("report", () -> {
            ProbeSql.execute("CREATE TABLE IF NOT EXISTS tx_demo (id INT)");
            ProbeSql.execute("DELETE FROM tx_demo");
            ProbeSql.execute("INSERT INTO tx_demo VALUES (1)");
            return ProbeSql.label();
        });
        assertEquals("report", label);

        assertThrows(RuntimeException.class, () -> transactionHelper.executeOn("report", () -> {
            ProbeSql.execute("INSERT INTO tx_demo VALUES (2)");
            throw new IllegalStateException("触发回滚");
        }));

        DynamicDataSourceContextHolder.push("report");
        try {
            assertEquals(1, ProbeSql.queryInt("SELECT COUNT(*) FROM tx_demo"));
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }

    private static DataSourceDefinition reportDefinition(String name, String url) {
        DataSourceDefinition definition = new DataSourceDefinition();
        definition.setName(name);
        definition.setDatabaseType(cn.slainc.datasource.enums.DatabaseType.H2);
        definition.setUrl(url);
        definition.setUsername("sa");
        definition.setPassword("");
        definition.setStatus(DataSourceStatus.ENABLED);
        definition.setDescription("运行时注册的报表库");
        return definition;
    }
}
