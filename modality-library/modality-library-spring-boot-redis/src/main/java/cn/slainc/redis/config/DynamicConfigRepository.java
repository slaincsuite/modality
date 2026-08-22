package cn.slainc.redis.config;

import cn.slainc.core.config.DynamicConfig;

import java.util.List;
import java.util.Optional;

public interface DynamicConfigRepository {

    List<DynamicConfig> findAll();

    Optional<DynamicConfig> findByKey(String key);

    List<DynamicConfig> findByGroup(String group);

    void save(DynamicConfig config);

    void delete(String key);
}
