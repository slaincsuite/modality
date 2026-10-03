package cn.slainc.modules.infra.service;

import cn.slainc.core.lang.BizAssert;
import cn.slainc.redis.config.RedisDynamicConfigService;
import cn.slainc.redis.autoconfigure.RedisProperties;
import cn.slainc.redis.cache.CacheInvalidationPublisher;
import cn.slainc.redis.key.RedisKeyManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.beans.factory.ObjectProvider;

public class CacheConsoleService {

    private static final String CACHE_PREFIX = "modality:cache:";

    private final RedisKeyManager keyManager;

    private final ObjectProvider<CacheInvalidationPublisher> invalidationPublisher;

    private final ObjectProvider<RedisDynamicConfigService> configService;

    private final RedisProperties redisProperties;

    public CacheConsoleService(RedisKeyManager keyManager,
                               ObjectProvider<CacheInvalidationPublisher> invalidationPublisher,
                               ObjectProvider<RedisDynamicConfigService> configService,
                               RedisProperties redisProperties) {
        this.keyManager = keyManager;
        this.invalidationPublisher = invalidationPublisher;
        this.configService = configService;
        this.redisProperties = redisProperties;
    }

    public List<Map<String, Object>> names() {
        List<String> keys = keyManager.scan(CACHE_PREFIX + "*", 2000);
        Map<String, Long> counts = new TreeMap<>();
        for (String key : keys) {
            String rest = key.substring(CACHE_PREFIX.length());
            int slash = rest.indexOf(':');
            String name = slash < 0 ? rest : rest.substring(0, slash);
            counts.merge(name, 1L, Long::sum);
        }
        List<Map<String, Object>> views = new ArrayList<>();
        for (Map.Entry<String, Long> entry : counts.entrySet()) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("name", entry.getKey());
            view.put("keys", entry.getValue());
            views.add(view);
        }
        return views;
    }

    public long clear(String name) {
        BizAssert.hasText(name, "缓存名不能为空");
        BizAssert.isTrue(name.matches("[A-Za-z0-9._\\-]+"), "缓存名只允许字母、数字、点、下划线与中划线");
        CacheInvalidationPublisher publisher = invalidationPublisher.getIfAvailable();
        if (publisher != null) {
            publisher.publishClear(name);
        }
        return keyManager.deleteByPrefix(CACHE_PREFIX + name + ":");
    }

    public Map<String, Object> warm() {
        RedisDynamicConfigService service = configService.getIfAvailable();
        Map<String, Object> view = new LinkedHashMap<>();
        if (service == null) {
            view.put("warmed", false);
            view.put("message", "动态配置服务未启用，无需预热");
            return view;
        }
        service.warmUp();
        view.put("warmed", true);
        view.put("version", service.currentVersion());
        view.put("message", "动态配置缓存已预热");
        return view;
    }

    public Map<String, Object> describe() {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("cachePrefix", CACHE_PREFIX);
        view.put("configStoreKey", "modality:config:" + redisProperties.getConfig().getStoreKey());
        view.put("defaultTtlSeconds", redisProperties.getCache().getDefaultTtl().toSeconds());
        view.put("secondLevelEnabled", redisProperties.getCache().isSecondLevelEnabled());
        return view;
    }
}
