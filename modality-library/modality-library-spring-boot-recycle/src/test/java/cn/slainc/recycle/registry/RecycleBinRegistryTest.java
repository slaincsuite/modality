package cn.slainc.recycle.registry;

import cn.slainc.core.exception.BizException;
import cn.slainc.recycle.config.RecycleProperties;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class RecycleBinRegistryTest {

    private static final RecyclableType DEMO = type("DEMO", "demo_file");

    private static final RecyclableType NOTE = type("note", "demo_note");

    @Test
    void shouldLookUpRegisteredTypesIgnoringCase() {
        RecycleBinRegistry registry = new RecycleBinRegistry(List.of(DEMO, NOTE), new RecycleProperties());

        assertThat(registry.find("demo")).isSameAs(DEMO);
        assertThat(registry.find(" NOTE ")).isSameAs(NOTE);
        assertThat(registry.find("missing")).isNull();
        assertThat(registry.all()).containsExactly(DEMO, NOTE);
    }

    @Test
    void shouldFailWhenTheTypeIsNotRegistered() {
        RecycleBinRegistry registry = new RecycleBinRegistry(List.of(DEMO), new RecycleProperties());

        assertThatThrownBy(() -> registry.require("TENANT"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未注册的可回收类型");
    }

    @Test
    void shouldKeepTheFirstRegistrationWhenATypeIsDuplicated() {
        RecycleBinRegistry registry = new RecycleBinRegistry(List.of(DEMO, type("demo", "other_table")),
                new RecycleProperties());

        assertThat(registry.all()).hasSize(1);
        assertThat(registry.require("DEMO")).isSameAs(DEMO);
    }

    @Test
    void shouldHonourTheConfiguredWhitelist() {
        RecycleProperties properties = new RecycleProperties();
        properties.setEntityTypes(List.of("DEMO"));
        RecycleBinRegistry registry = new RecycleBinRegistry(List.of(DEMO, NOTE), properties);

        assertThat(registry.enabled()).containsExactly(DEMO);
        assertThat(registry.isEnabled("demo")).isTrue();
        assertThat(registry.isEnabled("NOTE")).isFalse();
        assertThat(registry.requireEnabled("DEMO")).isSameAs(DEMO);
        assertThatThrownBy(() -> registry.requireEnabled("NOTE"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("回收站未启用该类型");
    }

    private static RecyclableType type(String name, String table) {
        return RecyclableType.of(name, DemoFile.class, mapper(), DemoFile::getId, DemoFile::getName, true);
    }

    @SuppressWarnings("unchecked")
    private static BaseMapper<DemoFile> mapper() {
        return mock(BaseMapper.class);
    }

    static class DemoFile {

        private Long id;

        private String name;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
