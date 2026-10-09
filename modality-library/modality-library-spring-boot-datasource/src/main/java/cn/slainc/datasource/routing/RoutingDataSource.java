package cn.slainc.datasource.routing;

import cn.slainc.datasource.exception.DataSourceException;
import cn.slainc.datasource.strategy.WeightedDynamicDataSourceStrategy;
import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.dynamic.datasource.ds.GroupDataSource;
import com.baomidou.dynamic.datasource.provider.DynamicDataSourceProvider;
import com.baomidou.dynamic.datasource.strategy.DynamicDataSourceStrategy;
import com.baomidou.dynamic.datasource.strategy.LoadBalanceDynamicDataSourceStrategy;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;

import javax.sql.DataSource;
import java.util.List;

public class RoutingDataSource extends DynamicRoutingDataSource {

    private Class<? extends DynamicDataSourceStrategy> strategyClass = LoadBalanceDynamicDataSourceStrategy.class;

    public RoutingDataSource(List<DynamicDataSourceProvider> providers) {
        super(providers);
    }

    public String primaryName() {
        return getPrimary();
    }

    // 框架只在分组创建时实例化策略，运行期换类影响不到已有分组，必须把策略重新装进每个分组
    @Override
    public void setStrategy(Class<? extends DynamicDataSourceStrategy> strategy) {
        strategyClass = strategy == null ? LoadBalanceDynamicDataSourceStrategy.class : strategy;
        super.setStrategy(strategyClass);
        for (GroupDataSource group : getGroupDataSources().values()) {
            group.setDynamicDataSourceStrategy(instantiate(strategyClass));
        }
    }

    public void enableWeightedStrategy() {
        if (LoadBalanceDynamicDataSourceStrategy.class.equals(strategyClass)) {
            setStrategy(WeightedDynamicDataSourceStrategy.class);
        }
    }

    @Override
    public DataSource determineDataSource() {
        if (MasterForceHolder.isForced() && DynamicDataSourceContextHolder.peek() == null) {
            String primary = getPrimary();
            if (primary != null && getDataSources().containsKey(primary)) {
                return getDataSource(primary);
            }
        }
        return super.determineDataSource();
    }

    private static DynamicDataSourceStrategy instantiate(Class<? extends DynamicDataSourceStrategy> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new DataSourceException("无法实例化数据源路由策略: " + type.getName(), exception);
        }
    }
}
