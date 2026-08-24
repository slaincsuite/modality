package cn.slainc.redis.config;

import cn.slainc.core.config.DynamicConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryDynamicConfigRepositoryTest {

    @Test
    void 保存查询分组与逻辑删除() {
        InMemoryDynamicConfigRepository repository = new InMemoryDynamicConfigRepository();
        repository.save(config("a.b", "1", "组一"));
        repository.save(config("a.c", "2", "组一"));
        repository.save(config("d.e", "3", "组二"));

        assertThat(repository.findAll()).hasSize(3);
        assertThat(repository.findByKey("a.b")).isPresent();
        assertThat(repository.findByGroup("组一")).hasSize(2);

        repository.delete("a.b");
        assertThat(repository.findByKey("a.b")).isEmpty();
        assertThat(repository.findAll()).hasSize(2);
    }

    @Test
    void 空键与分组为空的处理() {
        InMemoryDynamicConfigRepository repository = new InMemoryDynamicConfigRepository();
        assertThat(repository.findByKey(null)).isEmpty();
        repository.delete(null);
        assertThat(repository.findByGroup(null)).isEmpty();

        DynamicConfig config = config("no.group", "值", null);
        repository.save(config);
        assertThat(repository.findByGroup(null)).hasSize(1);
    }

    @Test
    void 保存时补齐审计字段() {
        InMemoryDynamicConfigRepository repository = new InMemoryDynamicConfigRepository();
        DynamicConfig config = config("x.y", "值", "组");
        repository.save(config);
        assertThat(config.getCreatedAt()).isNotNull();
        assertThat(config.getUpdatedAt()).isNotNull();
        assertThat(config.getDeleted()).isZero();

        assertThatThrownBy(() -> repository.save(new DynamicConfig()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("configKey 不能为空");
    }

    @Test
    void 清空仓储() {
        InMemoryDynamicConfigRepository repository = new InMemoryDynamicConfigRepository();
        repository.save(config("a", "1", "组"));
        repository.clear();
        assertThat(repository.findAll()).isEmpty();
    }

    private DynamicConfig config(String key, String value, String group) {
        DynamicConfig config = new DynamicConfig();
        config.setConfigKey(key);
        config.setConfigValue(value);
        config.setConfigGroup(group);
        return config;
    }
}
