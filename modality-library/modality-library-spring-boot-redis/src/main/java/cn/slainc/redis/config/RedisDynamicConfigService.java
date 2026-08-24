package cn.slainc.redis.config;

import cn.slainc.core.config.DynamicConfig;
import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.core.constant.CacheKeys;
import cn.slainc.core.event.ConfigChangedEvent;
import cn.slainc.redis.autoconfigure.RedisProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public class RedisDynamicConfigService implements DynamicConfigService {

    private static final Logger log = LoggerFactory.getLogger(RedisDynamicConfigService.class);

    private static final String SNAPSHOT_KEY = "all";

    private static final Set<String> TRUE_WORDS = Set.of("true", "1", "yes", "y", "on");

    private static final Set<String> FALSE_WORDS = Set.of("false", "0", "no", "n", "off");

    private final StringRedisTemplate stringRedisTemplate;

    private final DynamicConfigRepository repository;

    private final ObjectMapper objectMapper;

    private final ConfigValueCipher cipher;

    private final ConfigBroadcaster broadcaster;

    private final ApplicationEventPublisher eventPublisher;

    private final String nodeId = ConfigChangeMessage.newNodeId();

    private final String storeKey;

    private final String versionKey;

    private final String channel;

    private final boolean publishEnabled;

    private final Cache<String, DynamicConfig> localCache;

    private final Cache<String, Map<String, DynamicConfig>> snapshotCache;

    private final Cache<String, Long> localVersions;

    private final AtomicLong knownVersion = new AtomicLong();

    public RedisDynamicConfigService(StringRedisTemplate stringRedisTemplate,
                                     DynamicConfigRepository repository,
                                     ObjectMapper objectMapper,
                                     ConfigValueCipher cipher,
                                     ConfigBroadcaster broadcaster,
                                     RedisProperties properties) {
        this(stringRedisTemplate, repository, objectMapper, cipher, broadcaster, null, properties);
    }

    public RedisDynamicConfigService(StringRedisTemplate stringRedisTemplate,
                                     DynamicConfigRepository repository,
                                     ObjectMapper objectMapper,
                                     ConfigValueCipher cipher,
                                     ConfigBroadcaster broadcaster,
                                     ApplicationEventPublisher eventPublisher,
                                     RedisProperties properties) {
        if (stringRedisTemplate == null) {
            throw new IllegalArgumentException("stringRedisTemplate 不能为空");
        }
        if (objectMapper == null) {
            throw new IllegalArgumentException("objectMapper 不能为空");
        }
        this.stringRedisTemplate = stringRedisTemplate;
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.cipher = cipher == null ? new UnavailableConfigValueCipher() : cipher;
        this.broadcaster = broadcaster;
        this.eventPublisher = eventPublisher;
        RedisProperties.Config config = properties.getConfig();
        this.storeKey = CacheKeys.CONFIG + config.getStoreKey();
        this.versionKey = CacheKeys.CONFIG + "version";
        this.channel = CacheKeys.CONFIG + config.getBroadcastChannel();
        this.publishEnabled = config.isPublishEnabled() && broadcaster != null;
        long maxSize = Math.max(config.getLocalCacheMaxSize(), 1L);
        Duration ttl = config.getLocalCacheTtl();
        if (config.isLocalCacheEnabled()) {
            this.localCache = Caffeine.newBuilder().maximumSize(maxSize).expireAfterWrite(ttl).build();
            this.snapshotCache = Caffeine.newBuilder().maximumSize(64L).expireAfterWrite(ttl).build();
        } else {
            this.localCache = Caffeine.newBuilder().maximumSize(0L).build();
            this.snapshotCache = Caffeine.newBuilder().maximumSize(0L).build();
        }
        this.localVersions = Caffeine.newBuilder().maximumSize(maxSize).build();
        this.knownVersion.set(readVersion());
    }

    public static String channelOf(RedisProperties properties) {
        return CacheKeys.CONFIG + properties.getConfig().getBroadcastChannel();
    }

    public String nodeId() {
        return nodeId;
    }

    public String channel() {
        return channel;
    }

    public String storeKey() {
        return storeKey;
    }

    public long currentVersion() {
        return knownVersion.get();
    }

    public boolean isLocallyCached(String key) {
        return key != null && localCache.asMap().containsKey(key);
    }

    public DynamicConfigRepository getRepository() {
        return repository;
    }

    public String localCachedValue(String key) {
        DynamicConfig config = key == null ? null : localCache.getIfPresent(key);
        return config == null ? null : config.getConfigValue();
    }

    public void warmUp() {
        receive(ConfigChangeMessage.of(null, ConfigChangeMessage.Operation.REFRESH_ALL, readVersion(), nodeId));
    }

    @Override
    public Optional<String> findString(String key) {
        DynamicConfig config = findConfig(key);
        if (config == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(resolveValue(config));
    }

    @Override
    public String getString(String key, String defaultValue) {
        return findString(key).orElse(defaultValue);
    }

    @Override
    public int getInt(String key, int defaultValue) {
        Optional<String> value = findString(key);
        if (value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.get().trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("动态配置 [" + key + "] 的值不是合法整数：" + value.get());
        }
    }

    @Override
    public long getLong(String key, long defaultValue) {
        Optional<String> value = findString(key);
        if (value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.get().trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("动态配置 [" + key + "] 的值不是合法长整数：" + value.get());
        }
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        Optional<String> value = findString(key);
        if (value.isEmpty()) {
            return defaultValue;
        }
        String text = value.get().trim().toLowerCase();
        if (TRUE_WORDS.contains(text)) {
            return true;
        }
        if (FALSE_WORDS.contains(text)) {
            return false;
        }
        throw new IllegalArgumentException("动态配置 [" + key + "] 的值不是合法布尔值：" + value.get());
    }

    @Override
    public <T> T getObject(String key, Class<T> type) {
        return getObject(key, type, null);
    }

    @Override
    public <T> T getObject(String key, Class<T> type, T defaultValue) {
        Optional<String> value = findString(key);
        if (value.isEmpty() || value.get().isBlank()) {
            return defaultValue;
        }
        try {
            return objectMapper.readValue(value.get(), type);
        } catch (Exception exception) {
            log.warn("动态配置 [{}] 反序列化为 {} 失败，原因={}", key, type.getName(), exception.getMessage());
            return defaultValue;
        }
    }

    @Override
    public <T> List<T> getList(String key, Class<T> elementType) {
        Optional<String> value = findString(key);
        if (value.isEmpty() || value.get().isBlank()) {
            return new ArrayList<>();
        }
        try {
            JavaType javaType = objectMapper.getTypeFactory().constructCollectionType(List.class, elementType);
            return objectMapper.readValue(value.get(), javaType);
        } catch (Exception exception) {
            log.warn("动态配置 [{}] 反序列化为 List<{}> 失败，原因={}", key, elementType.getName(), exception.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public String getEncrypted(String key) {
        DynamicConfig config = findConfig(key);
        if (config == null || config.getConfigValue() == null) {
            return null;
        }
        if (Boolean.TRUE.equals(config.getEncrypted())) {
            return cipher.decrypt(config.getConfigValue());
        }
        return config.getConfigValue();
    }

    @Override
    public Map<String, String> getGroup(String group) {
        Map<String, String> result = new LinkedHashMap<>();
        for (DynamicConfig config : listByGroup(group)) {
            if (!Boolean.FALSE.equals(config.getEnabled())) {
                result.put(config.getConfigKey(), resolveValue(config));
            }
        }
        return result;
    }

    @Override
    public List<DynamicConfig> listByGroup(String group) {
        List<DynamicConfig> result = new ArrayList<>();
        for (DynamicConfig config : allConfigs().values()) {
            if (group == null ? config.getConfigGroup() == null : group.equals(config.getConfigGroup())) {
                result.add(config);
            }
        }
        result.sort(Comparator.comparing(DynamicConfig::getConfigKey));
        return result;
    }

    @Override
    public void set(String key, String value) {
        set(key, value, null);
    }

    @Override
    public void set(String key, String value, String group) {
        write(key, value, group, false);
    }

    @Override
    public void setEncrypted(String key, String value, String group) {
        write(key, value, group, true);
    }

    @Override
    public void remove(String key) {
        if (key == null) {
            return;
        }
        if (repository != null) {
            repository.delete(key);
        }
        stringRedisTemplate.opsForHash().delete(storeKey, key);
        long version = nextVersion();
        localCache.invalidate(key);
        snapshotCache.invalidateAll();
        localVersions.put(key, version);
        publish(ConfigChangeMessage.of(key, ConfigChangeMessage.Operation.REMOVE, version, nodeId));
    }

    @Override
    public void refresh(String key) {
        if (key == null) {
            return;
        }
        DynamicConfig config = loadFromStoreOrRepository(key);
        if (config == null) {
            localCache.invalidate(key);
        } else {
            localCache.put(key, config);
            localVersions.put(key, config.getVersion() == null ? 0L : config.getVersion().longValue());
        }
        snapshotCache.invalidateAll();
        publish(ConfigChangeMessage.of(key, ConfigChangeMessage.Operation.REFRESH, nextVersion(), nodeId));
    }

    @Override
    public void refreshAll() {
        localCache.invalidateAll();
        snapshotCache.invalidateAll();
        localVersions.invalidateAll();
        publish(ConfigChangeMessage.of(null, ConfigChangeMessage.Operation.REFRESH_ALL, nextVersion(), nodeId));
    }

    public void receive(ConfigChangeMessage message) {
        if (message == null || message.getOperation() == null) {
            return;
        }
        if (message.getOperation() == ConfigChangeMessage.Operation.REFRESH_ALL) {
            localCache.invalidateAll();
            snapshotCache.invalidateAll();
            localVersions.invalidateAll();
            knownVersion.accumulateAndGet(message.getVersion(), Math::max);
            return;
        }
        String key = message.getKey();
        if (key == null) {
            return;
        }
        long local = localVersionOf(key);
        // 版本号乐观锁：只有收到更大的版本号才刷新本地缓存
        if (message.getVersion() > 0 && message.getVersion() <= local) {
            return;
        }
        if (message.getOperation() == ConfigChangeMessage.Operation.REMOVE) {
            localCache.invalidate(key);
        } else {
            DynamicConfig config = loadFromStoreOrRepository(key);
            if (config == null) {
                localCache.invalidate(key);
            } else {
                localCache.put(key, config);
            }
        }
        snapshotCache.invalidateAll();
        localVersions.put(key, message.getVersion());
        knownVersion.accumulateAndGet(message.getVersion(), Math::max);
    }

    private void write(String key, String value, String group, boolean encrypted) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("动态配置的键不能为空");
        }
        DynamicConfig existing = repository == null ? null : repository.findByKey(key).orElse(null);
        DynamicConfig config = existing == null ? new DynamicConfig() : existing;
        config.setConfigKey(key);
        config.setConfigValue(encrypted ? cipher.encrypt(value) : value);
        config.setConfigGroup(group);
        config.setEncrypted(encrypted);
        if (config.getEnabled() == null) {
            config.setEnabled(Boolean.TRUE);
        }
        long version = nextVersion();
        config.setVersion((int) version);
        if (repository != null) {
            repository.save(config);
        }
        writeStore(config);
        localCache.put(key, config);
        snapshotCache.invalidateAll();
        localVersions.put(key, version);
        publish(ConfigChangeMessage.of(key, ConfigChangeMessage.Operation.SET, version, nodeId));
        if (eventPublisher != null) {
            eventPublisher.publishEvent(new ConfigChangedEvent(key, group,
                    existing == null ? null : existing.getConfigValue(), config.getConfigValue(), false));
        }
    }

    private DynamicConfig findConfig(String key) {
        if (key == null) {
            return null;
        }
        DynamicConfig cached = localCache.getIfPresent(key);
        if (cached != null) {
            return cached;
        }
        DynamicConfig config = loadFromStoreOrRepository(key);
        if (config != null) {
            localCache.put(key, config);
            localVersions.put(key, config.getVersion() == null ? 0L : config.getVersion().longValue());
        }
        return config;
    }

    private DynamicConfig loadFromStoreOrRepository(String key) {
        DynamicConfig config = readStore(key);
        if (config == null && repository != null) {
            config = repository.findByKey(key).orElse(null);
            if (config != null) {
                writeStore(config);
            }
        }
        return config;
    }

    private DynamicConfig readStore(String key) {
        Object raw = stringRedisTemplate.opsForHash().get(storeKey, key);
        if (raw == null) {
            return null;
        }
        try {
            return objectMapper.readValue(String.valueOf(raw), DynamicConfig.class);
        } catch (Exception exception) {
            log.warn("动态配置 [{}] 的 Redis 存储内容解析失败，原因={}", key, exception.getMessage());
            return null;
        }
    }

    private void writeStore(DynamicConfig config) {
        try {
            stringRedisTemplate.opsForHash().put(storeKey, config.getConfigKey(),
                    objectMapper.writeValueAsString(config));
        } catch (Exception exception) {
            throw new IllegalStateException("动态配置写入 Redis 存储失败：" + exception.getMessage(), exception);
        }
    }

    private Map<String, DynamicConfig> allConfigs() {
        return snapshotCache.get(SNAPSHOT_KEY, key -> loadAll());
    }

    private Map<String, DynamicConfig> loadAll() {
        Map<String, DynamicConfig> result = new LinkedHashMap<>();
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(storeKey);
        if (entries.isEmpty() && repository != null) {
            for (DynamicConfig config : repository.findAll()) {
                writeStore(config);
                result.put(config.getConfigKey(), config);
            }
            return result;
        }
        entries.forEach((field, value) -> {
            try {
                DynamicConfig config = objectMapper.readValue(String.valueOf(value), DynamicConfig.class);
                result.put(String.valueOf(field), config);
            } catch (Exception exception) {
                log.warn("动态配置 [{}] 的 Redis 存储内容解析失败，原因={}", field, exception.getMessage());
            }
        });
        return result;
    }

    private String resolveValue(DynamicConfig config) {
        if (config.getConfigValue() == null) {
            return null;
        }
        if (Boolean.TRUE.equals(config.getEncrypted())) {
            return cipher.decrypt(config.getConfigValue());
        }
        return config.getConfigValue();
    }

    private long nextVersion() {
        Long version = stringRedisTemplate.opsForValue().increment(versionKey);
        long current = version == null ? System.currentTimeMillis() : version;
        knownVersion.accumulateAndGet(current, Math::max);
        return current;
    }

    private long readVersion() {
        String value = stringRedisTemplate.opsForValue().get(versionKey);
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private long localVersionOf(String key) {
        Long version = localVersions.getIfPresent(key);
        return version == null ? 0L : version;
    }

    private void publish(ConfigChangeMessage message) {
        if (publishEnabled) {
            broadcaster.publish(message);
        }
    }
}
