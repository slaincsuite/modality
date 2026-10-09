package cn.slainc.datasource.config;

import cn.slainc.datasource.properties.DataSourceProperties;
import com.baomidou.dynamic.datasource.creator.DataSourceProperty;
import com.baomidou.dynamic.datasource.creator.druid.DruidConfig;

public final class PoolDefaults {

    private PoolDefaults() {
    }

    // 无条件预置：property.type 为空时 creator 仍可能选中 Druid，一旦选中而 maxWait 为默认 -1 就会永久阻塞
    public static void applyDruidMaxWait(DataSourceProperty property, DataSourceProperties properties) {
        DruidConfig druid = property.getDruid() == null ? new DruidConfig() : property.getDruid();
        if (druid.getMaxWait() == null) {
            druid.setMaxWait(properties.getPool().getMaxWaitMillis());
        }
        property.setDruid(druid);
    }
}
