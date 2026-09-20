package cn.slainc.thirdauth;

import cn.slainc.core.config.DynamicConfig;
import cn.slainc.core.config.DynamicConfigService;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class FakeConfigService implements DynamicConfigService {

    private final Map<String, String> values = new ConcurrentHashMap<>();

    public void put(String key, String value) {
        values.put(key, value);
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
        return getObject(key, type, null);
    }

    @Override
    public <T> T getObject(String key, Class<T> type, T defaultValue) {
        String value = values.get(key);
        return value == null ? defaultValue : cn.slainc.utils.json.JsonUtils.parse(value, type);
    }

    @Override
    public <T> List<T> getList(String key, Class<T> elementType) {
        String value = values.get(key);
        return value == null ? List.of() : cn.slainc.utils.json.JsonUtils.parseList(value, elementType);
    }

    @Override
    public String getEncrypted(String key) {
        return values.get(key);
    }

    @Override
    public Map<String, String> getGroup(String group) {
        return Map.of();
    }

    @Override
    public List<DynamicConfig> listByGroup(String group) {
        return List.of();
    }

    @Override
    public void set(String key, String value) {
        values.put(key, value);
    }

    @Override
    public void set(String key, String value, String group) {
        values.put(key, value);
    }

    @Override
    public void setEncrypted(String key, String value, String group) {
        values.put(key, value);
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
