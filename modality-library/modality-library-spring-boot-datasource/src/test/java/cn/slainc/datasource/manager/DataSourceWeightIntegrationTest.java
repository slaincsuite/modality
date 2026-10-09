package cn.slainc.datasource.manager;

import cn.slainc.datasource.DataSourceTestApplication;
import cn.slainc.datasource.definition.DataSourceDefinition;
import cn.slainc.datasource.enums.DataSourceStatus;
import cn.slainc.datasource.enums.DatabaseType;
import cn.slainc.datasource.routing.RoutingDataSource;
import cn.slainc.datasource.strategy.WeightedDynamicDataSourceStrategy;
import com.baomidou.dynamic.datasource.ds.GroupDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@SpringBootTest(classes = DataSourceTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DataSourceWeightIntegrationTest {

    private static final String WEIGHTED_NAME = "slave_9";

    private static final String WEIGHTED_URL = "jdbc:h2:mem:modality-ds-weighted;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private DynamicDataSourceManager manager;

    @AfterEach
    void tearDown() {
        if (manager.exists(WEIGHTED_NAME)) {
            manager.remove(WEIGHTED_NAME);
        }
        WeightedDynamicDataSourceStrategy.clear();
    }

    @Test
    @DisplayName("注册带权重的数据源后分组切换到加权策略")
    void weightedStrategyIsInstalledOnRegistration() {
        DataSourceDefinition definition = definition();
        definition.setWeight(5);

        manager.register(definition);

        GroupDataSource group = ((RoutingDataSource) dataSource).getGroupDataSources().get("slave");
        assertInstanceOf(WeightedDynamicDataSourceStrategy.class, group.getDynamicDataSourceStrategy());
        assertEquals(5, WeightedDynamicDataSourceStrategy.weightOf(WEIGHTED_NAME));
    }

    @Test
    @DisplayName("移除数据源后权重与游标一并清理")
    void weightIsRemovedWithDataSource() {
        manager.register(definition());

        manager.remove(WEIGHTED_NAME);

        assertEquals(WeightedDynamicDataSourceStrategy.DEFAULT_WEIGHT,
                WeightedDynamicDataSourceStrategy.weightOf(WEIGHTED_NAME));
    }

    private static DataSourceDefinition definition() {
        DataSourceDefinition definition = new DataSourceDefinition();
        definition.setName(WEIGHTED_NAME);
        definition.setDatabaseType(DatabaseType.H2);
        definition.setUrl(WEIGHTED_URL);
        definition.setUsername("sa");
        definition.setPassword("");
        definition.setStatus(DataSourceStatus.ENABLED);
        return definition;
    }
}
