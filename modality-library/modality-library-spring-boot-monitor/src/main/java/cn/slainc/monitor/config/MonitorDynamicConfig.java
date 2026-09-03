package cn.slainc.monitor.config;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.core.event.ConfigChangedEvent;
import cn.slainc.utils.json.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class MonitorDynamicConfig {

    private static final Logger log = LoggerFactory.getLogger(MonitorDynamicConfig.class);

    private final ObjectProvider<DynamicConfigService> provider;

    private final Environment environment;

    private final long cacheMillis;

    private final Map<String, CachedValue> cache = new ConcurrentHashMap<>();

    public MonitorDynamicConfig(ObjectProvider<DynamicConfigService> provider, Environment environment, long cacheMillis) {
        if (environment == null) {
            throw new IllegalArgumentException("environment 不能为空");
        }
        this.provider = provider;
        this.environment = environment;
        this.cacheMillis = Math.max(cacheMillis, 0L);
    }

    public Optional<String> find(String key) {
        long now = System.currentTimeMillis();
        CachedValue cached = cache.get(key);
        if (cached != null && cached.expiresAt() > now) {
            return Optional.ofNullable(cached.value());
        }
        String value = readFromService(key);
        if (value == null) {
            value = environment.getProperty(key);
        }
        cache.put(key, new CachedValue(value, now + cacheMillis));
        return Optional.ofNullable(value);
    }

    public String getString(String key, String defaultValue) {
        return find(key).filter(value -> !value.isBlank()).orElse(defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        return toNumber(key, defaultValue, Integer::parseInt).intValue();
    }

    public long getLong(String key, long defaultValue) {
        return toNumber(key, defaultValue, Long::parseLong).longValue();
    }

    public double getDouble(String key, double defaultValue) {
        return toNumber(key, defaultValue, Double::parseDouble).doubleValue();
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String value = find(key).orElse(null);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        if ("true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value) || "0".equals(value) || "no".equalsIgnoreCase(value)) {
            return false;
        }
        return defaultValue;
    }

    public <T> List<T> getList(String key, Class<T> elementType) {
        String value = find(key).orElse(null);
        if (value == null || value.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return JsonUtils.parseList(value, elementType);
        } catch (RuntimeException exception) {
            log.warn("动态配置 {} 不是合法的 JSON 数组，已忽略: {}", key, exception.getMessage());
            return new ArrayList<>();
        }
    }

    public Map<String, String> getGroup(String group) {
        DynamicConfigService service = provider.getIfAvailable();
        if (service == null) {
            return Map.of();
        }
        try {
            return service.getGroup(group);
        } catch (RuntimeException exception) {
            log.warn("读取动态配置分组 {} 失败: {}", group, exception.getMessage());
            return Map.of();
        }
    }

    public void invalidate() {
        cache.clear();
    }

    @EventListener
    public void onConfigChanged(ConfigChangedEvent event) {
        invalidate();
    }

    private String readFromService(String key) {
        DynamicConfigService service = provider.getIfAvailable();
        if (service == null) {
            return null;
        }
        try {
            return service.findString(key).orElse(null);
        } catch (RuntimeException exception) {
            log.warn("读取动态配置 {} 失败，回退到本地配置: {}", key, exception.getMessage());
            return null;
        }
    }

    private Number toNumber(String key, Number defaultValue, java.util.function.Function<String, Number> parser) {
        String value = find(key).orElse(null);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return parser.apply(value.trim());
        } catch (NumberFormatException exception) {
            log.warn("动态配置 {} 的值 {} 不是数字，使用默认值 {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    private record CachedValue(String value, long expiresAt) {
    }
}
