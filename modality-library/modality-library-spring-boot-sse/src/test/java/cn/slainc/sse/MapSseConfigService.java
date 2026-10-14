package cn.slainc.sse;

import cn.slainc.core.config.DynamicConfig;
import cn.slainc.core.config.DynamicConfigService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class MapSseConfigService implements DynamicConfigService {

    private final Map<String, String> values = new HashMap<>();

    public MapSseConfigService put(String key, String value) {
        values.put(key, value);
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
        throw new UnsupportedOperationException("测试桩不支持 getObject");
    }

    @Override
    public <T> T getObject(String key, Class<T> type, T defaultValue) {
        throw new UnsupportedOperationException("测试桩不支持 getObject");
    }

    @Override
    public <T> List<T> getList(String key, Class<T> elementType) {
        throw new UnsupportedOperationException("测试桩不支持 getList");
    }

    @Override
    public String getEncrypted(String key) {
        throw new UnsupportedOperationException("测试桩不支持 getEncrypted");
    }

    @Override
    public Map<String, String> getGroup(String group) {
        throw new UnsupportedOperationException("测试桩不支持 getGroup");
    }

    @Override
    public List<DynamicConfig> listByGroup(String group) {
        throw new UnsupportedOperationException("测试桩不支持 listByGroup");
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
