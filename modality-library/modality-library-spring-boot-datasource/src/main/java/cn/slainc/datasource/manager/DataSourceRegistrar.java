package cn.slainc.datasource.manager;

import cn.slainc.datasource.crypto.DataSourcePasswordEncryptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;

public class DataSourceRegistrar implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(DataSourceRegistrar.class);

    private final DynamicDataSourceManager manager;

    private final DataSourcePasswordEncryptor encryptor;

    public DataSourceRegistrar(DynamicDataSourceManager manager) {
        this(manager, null);
    }

    public DataSourceRegistrar(DynamicDataSourceManager manager, DataSourcePasswordEncryptor encryptor) {
        this.manager = manager;
        this.encryptor = encryptor;
    }

    @Override
    public void afterSingletonsInstantiated() {
        try {
            int loaded = manager.reload();
            log.info("从配置表装载动态数据源 {} 个，当前共 {} 个", loaded, manager.dataSourceNames().size());
        } catch (Exception exception) {
            log.warn("装载动态数据源失败，已跳过: {}", exception.getMessage());
        }
        warnWhenPasswordStoredInPlainText();
    }

    private void warnWhenPasswordStoredInPlainText() {
        if (encryptor == null || encryptor.isEnabled() || manager.dataSourceNames().isEmpty()) {
            return;
        }
        log.warn("modality.datasource.encrypt.enabled=false，数据源密码将以明文写入配置表；"
                + "生产环境请配置 modality.datasource.encrypt.key 并开启 modality.datasource.encrypt.enabled");
    }
}
