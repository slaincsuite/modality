package cn.slainc.redis.config;

import cn.slainc.core.config.DynamicConfig;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryDynamicConfigRepository implements DynamicConfigRepository {

    private final Map<String, DynamicConfig> store = new ConcurrentHashMap<>();

    @Override
    public List<DynamicConfig> findAll() {
        List<DynamicConfig> result = new ArrayList<>();
        for (DynamicConfig config : store.values()) {
            if (!Integer.valueOf(1).equals(config.getDeleted())) {
                result.add(config);
            }
        }
        result.sort(Comparator.comparing(DynamicConfig::getConfigKey));
        return result;
    }

    @Override
    public Optional<DynamicConfig> findByKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        DynamicConfig config = store.get(key);
        if (config == null || Integer.valueOf(1).equals(config.getDeleted())) {
            return Optional.empty();
        }
        return Optional.of(config);
    }

    @Override
    public List<DynamicConfig> findByGroup(String group) {
        List<DynamicConfig> result = new ArrayList<>();
        for (DynamicConfig config : findAll()) {
            if (group == null ? config.getConfigGroup() == null : group.equals(config.getConfigGroup())) {
                result.add(config);
            }
        }
        return result;
    }

    @Override
    public void save(DynamicConfig config) {
        if (config == null || config.getConfigKey() == null) {
            throw new IllegalArgumentException("动态配置的 configKey 不能为空");
        }
        if (config.getUpdatedAt() == null) {
            config.setUpdatedAt(LocalDateTime.now());
        }
        if (config.getCreatedAt() == null) {
            config.setCreatedAt(config.getUpdatedAt());
        }
        if (config.getDeleted() == null) {
            config.setDeleted(0);
        }
        store.put(config.getConfigKey(), config);
    }

    @Override
    public void delete(String key) {
        if (key == null) {
            return;
        }
        DynamicConfig config = store.get(key);
        if (config != null) {
            config.setDeleted(1);
            config.setUpdatedAt(LocalDateTime.now());
        }
    }

    public void clear() {
        store.clear();
    }
}
