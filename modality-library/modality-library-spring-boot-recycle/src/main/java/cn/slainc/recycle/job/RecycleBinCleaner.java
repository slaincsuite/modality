package cn.slainc.recycle.job;

import cn.slainc.recycle.spi.RecycleItemStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDateTime;

public class RecycleBinCleaner {

    private static final Logger LOGGER = LoggerFactory.getLogger(RecycleBinCleaner.class);

    private final ObjectProvider<RecycleItemStore> recycleItemStoreProvider;

    public RecycleBinCleaner(ObjectProvider<RecycleItemStore> recycleItemStoreProvider) {
        this.recycleItemStoreProvider = recycleItemStoreProvider;
    }

    public int purgeExpired() {
        RecycleItemStore store = recycleItemStoreProvider == null ? null : recycleItemStoreProvider.getIfAvailable();
        if (store == null) {
            LOGGER.debug("未找到可用的 RecycleItemStore，跳过过期回收项清理");
            return 0;
        }
        try {
            return store.deleteExpired(LocalDateTime.now());
        } catch (RuntimeException e) {
            LOGGER.error("清理过期回收项失败: {}", e.getMessage(), e);
            return 0;
        }
    }
}
