package cn.slainc.monitor.db;

import cn.slainc.monitor.support.MonitorTestSupport;
import com.alibaba.druid.pool.DruidDataSource;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

class DataSourcePoolMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @Test
    @DisplayName("Druid 连接池指标：活跃、空闲、等待、上限与执行/事务计数")
    void shouldBindDruidPoolGauges() throws SQLException {
        DruidDataSource dataSource = druid("monitor-druid");
        try {
            try (Connection connection = dataSource.getConnection()) {
                assertThat(connection).isNotNull();
            }
            DruidPoolMetricsBinder binder = new DruidPoolMetricsBinder(provider(dataSource),
                    new DataSourcePoolIntrospector(), MonitorTestSupport.metricsTemplate(registry), 1000L);
            binder.bindTo(registry);
            assertThat(poolCount("druid")).isEqualTo(1d);
            assertThat(gauge(DruidPoolMetricsBinder.MAX, "monitor-druid", "druid")).isEqualTo(6d);
            assertThat(gauge(DruidPoolMetricsBinder.IDLE, "monitor-druid", "druid")).isEqualTo(1d);
            assertThat(gauge(DruidPoolMetricsBinder.ACTIVE, "monitor-druid", "druid")).isZero();
            assertThat(gauge(DruidPoolMetricsBinder.PENDING, "monitor-druid", "druid")).isZero();
            assertThat(registry.get(DruidPoolMetricsBinder.EXECUTE_COUNT).tag("pool", "monitor-druid").gauge())
                    .isNotNull();
            assertThat(registry.get(DruidPoolMetricsBinder.COMMIT_COUNT).tag("pool", "monitor-druid").gauge().value())
                    .isNotNegative();
            assertThat(registry.get(DruidPoolMetricsBinder.ROLLBACK_COUNT).tag("pool", "monitor-druid").gauge())
                    .isNotNull();
            assertThat(registry.get(DruidPoolMetricsBinder.ERROR_COUNT).tag("pool", "monitor-druid").gauge())
                    .isNotNull();
        } finally {
            dataSource.close();
        }
    }

    @Test
    @DisplayName("能反射解包 dynamic-datasource 风格的嵌套数据源")
    void shouldUnwrapNestedDataSources() {
        DruidDataSource druid = druid("nested-druid");
        try {
            Map<String, DataSource> nested = new LinkedHashMap<>();
            nested.put("master", druid);
            DataSource routing = new FakeRoutingDataSource(nested);
            Map<String, DataSource> pools = new DataSourcePoolIntrospector().flatten(List.of(routing));
            assertThat(pools).containsOnlyKeys("master").containsValue(druid);
            assertThat(pools.get("master")).isSameAs(druid);
            assertThat(new DataSourcePoolIntrospector().flatten(null)).isEmpty();
        } finally {
            druid.close();
        }
    }

    private double poolCount(String type) {
        return registry.get(AbstractPoolMetricsBinder.POOL_COUNT).tag("type", type).gauge().value();
    }

    private double gauge(String name, String pool, String type) {
        return registry.get(name).tag("pool", pool).tag("type", type).gauge().value();
    }

    private static ObjectProvider<DataSource> provider(DataSource dataSource) {
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        factory.addBean("dataSource", dataSource);
        return factory.getBeanProvider(DataSource.class);
    }

    private static DruidDataSource druid(String name) {
        DruidDataSource dataSource = new DruidDataSource();
        dataSource.setUrl("jdbc:h2:mem:monitor_" + name + ";DB_CLOSE_DELAY=-1;MODE=MySQL");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        dataSource.setName(name);
        dataSource.setMaxActive(6);
        dataSource.setInitialSize(1);
        return dataSource;
    }

    private static class FakeRoutingDataSource implements DataSource {

        private final Map<String, DataSource> dataSources;

        private FakeRoutingDataSource(Map<String, DataSource> dataSources) {
            this.dataSources = dataSources;
        }

        public Map<String, DataSource> getDataSources() {
            return dataSources;
        }

        @Override
        public Connection getConnection() {
            throw new UnsupportedOperationException("测试替身不提供连接");
        }

        @Override
        public Connection getConnection(String username, String password) {
            throw new UnsupportedOperationException("测试替身不提供连接");
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException("测试替身不支持");
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("测试替身不支持");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }
}
