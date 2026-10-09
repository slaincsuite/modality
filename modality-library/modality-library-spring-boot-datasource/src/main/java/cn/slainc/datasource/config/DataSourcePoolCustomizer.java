package cn.slainc.datasource.config;

import cn.slainc.datasource.enums.PoolType;
import cn.slainc.datasource.manager.DynamicDataSourceManager;
import cn.slainc.datasource.properties.DataSourceProperties;
import com.baomidou.dynamic.datasource.creator.DataSourceProperty;
import com.baomidou.dynamic.datasource.spring.boot.autoconfigure.DynamicDataSourceProperties;
import com.baomidou.dynamic.datasource.spring.boot.autoconfigure.DynamicDataSourcePropertiesCustomizer;

import javax.sql.DataSource;

public class DataSourcePoolCustomizer implements DynamicDataSourcePropertiesCustomizer {

    private final DataSourceProperties properties;

    public DataSourcePoolCustomizer(DataSourceProperties properties) {
        this.properties = properties;
    }

    @Override
    public void customize(DynamicDataSourceProperties dynamicProperties) {
        PoolType poolType = properties.getPoolType();
        if (dynamicProperties.getDatasource() == null) {
            return;
        }
        Class<? extends DataSource> type = poolType == null || poolType == PoolType.HIKARI
                ? null : DynamicDataSourceManager.resolvePoolClass(poolType);
        for (DataSourceProperty property : dynamicProperties.getDatasource().values()) {
            if (type != null && property.getType() == null) {
                property.setType(type);
            }
            PoolDefaults.applyDruidMaxWait(property, properties);
        }
    }
}
