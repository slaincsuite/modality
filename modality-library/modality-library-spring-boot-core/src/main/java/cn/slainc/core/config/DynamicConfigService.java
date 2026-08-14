package cn.slainc.core.config;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface DynamicConfigService {

    Optional<String> findString(String key);

    String getString(String key, String defaultValue);

    int getInt(String key, int defaultValue);

    long getLong(String key, long defaultValue);

    boolean getBoolean(String key, boolean defaultValue);

    <T> T getObject(String key, Class<T> type);

    <T> T getObject(String key, Class<T> type, T defaultValue);

    <T> List<T> getList(String key, Class<T> elementType);

    String getEncrypted(String key);

    Map<String, String> getGroup(String group);

    List<DynamicConfig> listByGroup(String group);

    void set(String key, String value);

    void set(String key, String value, String group);

    void setEncrypted(String key, String value, String group);

    void remove(String key);

    void refresh(String key);

    void refreshAll();
}
