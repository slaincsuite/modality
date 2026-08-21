package cn.slainc.datasource.config;

import cn.slainc.datasource.enums.PoolType;
import cn.slainc.datasource.exception.DataSourceException;
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
        if (poolType == null || poolType == PoolType.HIKARI || dynamicProperties.getDatasource() == null) {
            return;
        }
        Class<? extends DataSource> type = resolve(poolType);
        for (DataSourceProperty property : dynamicProperties.getDatasource().values()) {
            if (property.getType() == null) {
                property.setType(type);
            }
        }
    }

    private static Class<? extends DataSource> resolve(PoolType poolType) {
        try {
            return Class.forName(poolType.getClassName()).asSubclass(DataSource.class);
        } catch (ClassNotFoundException exception) {
            throw new DataSourceException("启用 " + poolType.getDescription()
                    + " 连接池需要把对应依赖加入运行期类路径: " + poolType.getClassName(), exception);
        }
    }
}
