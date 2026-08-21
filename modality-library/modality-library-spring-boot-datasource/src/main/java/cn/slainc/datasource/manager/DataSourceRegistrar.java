package cn.slainc.datasource.manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;

public class DataSourceRegistrar implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(DataSourceRegistrar.class);

    private final DynamicDataSourceManager manager;

    public DataSourceRegistrar(DynamicDataSourceManager manager) {
        this.manager = manager;
    }

    @Override
    public void afterSingletonsInstantiated() {
        try {
            int loaded = manager.reload();
            log.info("从配置表装载动态数据源 {} 个，当前共 {} 个", loaded, manager.dataSourceNames().size());
        } catch (Exception exception) {
            log.warn("装载动态数据源失败，已跳过: {}", exception.getMessage());
        }
    }
}
