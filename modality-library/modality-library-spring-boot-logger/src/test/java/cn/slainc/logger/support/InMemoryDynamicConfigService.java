package cn.slainc.logger.support;

import cn.slainc.core.config.DynamicConfig;
import cn.slainc.core.config.DynamicConfigService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryDynamicConfigService implements DynamicConfigService {

    private final Map<String, String> values = new ConcurrentHashMap<>();

    private final Map<String, Map<String, String>> groups = new ConcurrentHashMap<>();

    public InMemoryDynamicConfigService put(String key, String value) {
        values.put(key, value);
        return this;
    }

    public InMemoryDynamicConfigService putGroup(String group, String key, String value) {
        groups.computeIfAbsent(group, ignored -> new ConcurrentHashMap<>()).put(key, value);
        return this;
    }

    @Override
    public Optional<String> findString(String key) {
        return Optional.ofNullable(values.get(key));
    }

    @Override
    public String getString(String key, String defaultValue) {
        return values.getOrDefault(key, defaultValue);
    }

    @Override
    public int getInt(String key, int defaultValue) {
        String value = values.get(key);
        return value == null ? defaultValue : Integer.parseInt(value);
    }

    @Override
    public long getLong(String key, long defaultValue) {
        String value = values.get(key);
        return value == null ? defaultValue : Long.parseLong(value);
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        String value = values.get(key);
        return value == null ? defaultValue : Boolean.parseBoolean(value);
    }

    @Override
    public <T> T getObject(String key, Class<T> type) {
        String value = values.get(key);
        if (value == null) {
            return null;
        }
        if (type == Double.class) {
            return type.cast(Double.valueOf(value));
        }
        if (type == Integer.class) {
            return type.cast(Integer.valueOf(value));
        }
        if (type == Long.class) {
            return type.cast(Long.valueOf(value));
        }
        return type.cast(value);
    }

    @Override
    public <T> T getObject(String key, Class<T> type, T defaultValue) {
        T value = getObject(key, type);
        return value == null ? defaultValue : value;
    }

    @Override
    public <T> List<T> getList(String key, Class<T> elementType) {
        return List.of();
    }

    @Override
    public String getEncrypted(String key) {
        return values.get(key);
    }

    @Override
    public Map<String, String> getGroup(String group) {
        Map<String, String> values = groups.get(group);
        return values == null ? new LinkedHashMap<>() : new LinkedHashMap<>(values);
    }

    @Override
    public List<DynamicConfig> listByGroup(String group) {
        List<DynamicConfig> configs = new ArrayList<>();
        for (Map.Entry<String, String> entry : getGroup(group).entrySet()) {
            DynamicConfig config = new DynamicConfig();
            config.setConfigKey(entry.getKey());
            config.setConfigValue(entry.getValue());
            config.setConfigGroup(group);
            configs.add(config);
        }
        return configs;
    }

    @Override
    public void set(String key, String value) {
        values.put(key, value);
    }

    @Override
    public void set(String key, String value, String group) {
        values.put(key, value);
        putGroup(group, key, value);
    }

    @Override
    public void setEncrypted(String key, String value, String group) {
        set(key, value, group);
    }

    @Override
    public void remove(String key) {
        values.remove(key);
    }

    @Override
    public void refresh(String key) {
    }

    @Override
    public void refreshAll() {
    }
}
