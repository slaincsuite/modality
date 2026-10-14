package cn.slainc.changeaudit.support;

import cn.slainc.changeaudit.spi.SnapshotLoader;
import cn.slainc.utils.collection.CollectionUtils;
import cn.slainc.utils.string.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SnapshotLoaderRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(SnapshotLoaderRegistry.class);

    private final Map<String, SnapshotLoader> loaders;

    public SnapshotLoaderRegistry(List<SnapshotLoader> loaders) {
        Map<String, SnapshotLoader> registered = new LinkedHashMap<>();
        if (CollectionUtils.isNotEmpty(loaders)) {
            for (SnapshotLoader loader : loaders) {
                if (loader == null || StringUtils.isBlank(loader.type())) {
                    LOGGER.warn("忽略未声明 type 的快照装载器: {}", loader == null ? null : loader.getClass().getName());
                    continue;
                }
                String key = key(loader.type());
                SnapshotLoader exists = registered.putIfAbsent(key, loader);
                if (exists != null) {
                    LOGGER.warn("快照装载器 {} 重复，保留 {} 并忽略 {}", loader.type(), exists.getClass().getName(),
                            loader.getClass().getName());
                }
            }
        }
        this.loaders = Map.copyOf(registered);
    }

    public SnapshotLoader find(String type) {
        return StringUtils.isBlank(type) ? null : loaders.get(key(type));
    }

    public boolean contains(String type) {
        return find(type) != null;
    }

    public List<String> types() {
        List<String> types = new ArrayList<>(loaders.size());
        loaders.values().forEach(loader -> types.add(loader.type()));
        return List.copyOf(types);
    }

    public Object load(String type, String id) {
        SnapshotLoader loader = find(type);
        if (loader == null || StringUtils.isBlank(id)) {
            return null;
        }
        try {
            return loader.load(id.trim());
        } catch (RuntimeException e) {
            LOGGER.warn("装载 {} 的 {} 快照失败: {}", type, id, e.getMessage());
            return null;
        }
    }

    private static String key(String type) {
        return type == null ? null : type.trim().toUpperCase(Locale.ROOT);
    }
}
