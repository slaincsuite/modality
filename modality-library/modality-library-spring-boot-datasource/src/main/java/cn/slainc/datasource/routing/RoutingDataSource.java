package cn.slainc.datasource.routing;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.dynamic.datasource.provider.DynamicDataSourceProvider;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;

import javax.sql.DataSource;
import java.util.List;

public class RoutingDataSource extends DynamicRoutingDataSource {

    public RoutingDataSource(List<DynamicDataSourceProvider> providers) {
        super(providers);
    }

    public String primaryName() {
        return getPrimary();
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
}
