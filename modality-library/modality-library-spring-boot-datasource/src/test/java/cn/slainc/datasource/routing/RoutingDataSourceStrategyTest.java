package cn.slainc.datasource.routing;

import com.baomidou.dynamic.datasource.ds.GroupDataSource;
import com.baomidou.dynamic.datasource.provider.DynamicDataSourceProvider;
import com.baomidou.dynamic.datasource.strategy.LoadBalanceDynamicDataSourceStrategy;
import com.baomidou.dynamic.datasource.strategy.RandomDynamicDataSourceStrategy;
import cn.slainc.datasource.strategy.WeightedDynamicDataSourceStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class RoutingDataSourceStrategyTest {

    @Test
    @DisplayName("启用加权策略会替换已有分组内的策略实例")
    void enablingWeightedStrategyReplacesExistingGroupStrategy() {
        RoutingDataSource routing = routing(Map.of("slave_1", dataSource("slave_1"), "slave_2", dataSource("slave_2")));
        GroupDataSource group = routing.getGroupDataSources().get("slave");
        assertInstanceOf(LoadBalanceDynamicDataSourceStrategy.class, group.getDynamicDataSourceStrategy());

        routing.enableWeightedStrategy();

        assertInstanceOf(WeightedDynamicDataSourceStrategy.class, group.getDynamicDataSourceStrategy());
    }

    @Test
    @DisplayName("新建分组沿用当前策略类")
    void newGroupUsesCurrentStrategyClass() {
        RoutingDataSource routing = routing(Map.of("slave_1", dataSource("slave_1")));

        routing.enableWeightedStrategy();
        routing.addDataSource("readonly_1", dataSource("readonly_1"));

        assertInstanceOf(WeightedDynamicDataSourceStrategy.class,
                routing.getGroupDataSources().get("readonly").getDynamicDataSourceStrategy());
    }

    @Test
    @DisplayName("使用方自定义策略不会被加权策略覆盖")
    void customStrategyIsNotReplaced() {
        RoutingDataSource routing = routing(Map.of("slave_1", dataSource("slave_1"), "slave_2", dataSource("slave_2")));

        routing.setStrategy(RandomDynamicDataSourceStrategy.class);
        routing.enableWeightedStrategy();

        assertInstanceOf(RandomDynamicDataSourceStrategy.class,
                routing.getGroupDataSources().get("slave").getDynamicDataSourceStrategy());
    }

    @Test
    @DisplayName("主库名跟随运行期切换")
    void primaryNameFollowsRuntimeSwitch() {
        RoutingDataSource routing = routing(Map.of("master", dataSource("master"), "standby", dataSource("standby")));

        routing.setPrimary("standby");

        org.junit.jupiter.api.Assertions.assertEquals("standby", routing.primaryName());
    }

    private static RoutingDataSource routing(Map<String, DataSource> dataSources) {
        RoutingDataSource routing = new RoutingDataSource(List.of(new FixedProvider(dataSources)));
        routing.afterPropertiesSet();
        return routing;
    }

    private static DataSource dataSource(String name) {
        return new DriverManagerDataSource("jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1;MODE=MySQL", "sa", "");
    }

    private static class FixedProvider implements DynamicDataSourceProvider {

        private final Map<String, DataSource> dataSources;

        FixedProvider(Map<String, DataSource> dataSources) {
            this.dataSources = new LinkedHashMap<>(dataSources);
        }

        @Override
        public Map<String, DataSource> loadDataSources() {
            return dataSources;
        }
    }
}
