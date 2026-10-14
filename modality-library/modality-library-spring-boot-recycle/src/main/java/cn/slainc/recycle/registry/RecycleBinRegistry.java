package cn.slainc.recycle.registry;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;
import cn.slainc.recycle.config.RecycleProperties;
import cn.slainc.utils.collection.CollectionUtils;
import cn.slainc.utils.string.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RecycleBinRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(RecycleBinRegistry.class);

    private final List<RecyclableType> types;

    private final Map<String, RecyclableType> typesByKey;

    private final List<String> whitelist;

    public RecycleBinRegistry(List<RecyclableType> types, RecycleProperties properties) {
        Map<String, RecyclableType> registered = new LinkedHashMap<>();
        if (CollectionUtils.isNotEmpty(types)) {
            for (RecyclableType type : types) {
                if (type == null || StringUtils.isBlank(type.type())) {
                    continue;
                }
                String key = key(type.type());
                RecyclableType exists = registered.putIfAbsent(key, type);
                if (exists != null) {
                    LOGGER.warn("可回收类型 {} 重复注册，保留 {} 并忽略 {}", type.type(),
                            exists.entityClass().getName(), type.entityClass().getName());
                }
            }
        }
        this.types = List.copyOf(registered.values());
        this.typesByKey = Map.copyOf(registered);
        List<String> configured = properties == null ? null : properties.getEntityTypes();
        List<String> keys = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(configured)) {
            for (String item : configured) {
                if (StringUtils.isNotBlank(item)) {
                    keys.add(key(item));
                }
            }
        }
        this.whitelist = List.copyOf(keys);
    }

    public List<RecyclableType> all() {
        return types;
    }

    public List<RecyclableType> enabled() {
        if (whitelist.isEmpty()) {
            return types;
        }
        List<RecyclableType> result = new ArrayList<>();
        for (RecyclableType type : types) {
            if (whitelist.contains(key(type.type()))) {
                result.add(type);
            }
        }
        return result;
    }

    public RecyclableType find(String type) {
        return StringUtils.isBlank(type) ? null : typesByKey.get(key(type));
    }

    public RecyclableType require(String type) {
        RecyclableType found = find(type);
        if (found == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "未注册的可回收类型: " + type);
        }
        return found;
    }

    public boolean isEnabled(String type) {
        return whitelist.isEmpty() ? find(type) != null : whitelist.contains(key(type));
    }

    public RecyclableType requireEnabled(String type) {
        RecyclableType found = require(type);
        if (!whitelist.isEmpty() && !whitelist.contains(key(type))) {
            throw new BizException(ResultCode.DATA_STATE_INVALID, "回收站未启用该类型: " + type);
        }
        return found;
    }

    private static String key(String type) {
        return type == null ? null : type.trim().toUpperCase(Locale.ROOT);
    }
}
