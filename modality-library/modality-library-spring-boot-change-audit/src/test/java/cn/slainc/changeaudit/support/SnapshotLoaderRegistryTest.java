package cn.slainc.changeaudit.support;

import cn.slainc.changeaudit.spi.SnapshotLoader;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class SnapshotLoaderRegistryTest {

    @Test
    void looksUpLoadersIgnoringCase() {
        SnapshotLoader tenant = loader("TENANT", id -> "tenant-" + id);
        SnapshotLoaderRegistry registry = new SnapshotLoaderRegistry(List.of(tenant));

        assertThat(registry.contains("tenant")).isTrue();
        assertThat(registry.find(" TENANT ")).isSameAs(tenant);
        assertThat(registry.types()).containsExactly("TENANT");
        assertThat(registry.load("TENANT", "7")).isEqualTo("tenant-7");
    }

    @Test
    void returnsNullForUnknownOrBlankRequests() {
        SnapshotLoaderRegistry registry = new SnapshotLoaderRegistry(List.of(loader("TENANT", id -> "x")));

        assertThat(registry.load("USER", "7")).isNull();
        assertThat(registry.load("TENANT", "  ")).isNull();
        assertThat(registry.load(null, "7")).isNull();
        assertThat(registry.find(null)).isNull();
    }

    @Test
    void keepsTheFirstDuplicateLoader() {
        SnapshotLoader first = loader("TENANT", id -> "first");
        SnapshotLoader second = loader("tenant", id -> "second");
        SnapshotLoaderRegistry registry = new SnapshotLoaderRegistry(List.of(first, second));

        assertThat(registry.load("TENANT", "1")).isEqualTo("first");
    }

    @Test
    void ignoresFailuresAndLoadersWithoutAType() {
        SnapshotLoader broken = loader("BROKEN", id -> {
            throw new IllegalStateException("boom");
        });
        SnapshotLoader noType = loader(" ", id -> "x");
        SnapshotLoaderRegistry registry = new SnapshotLoaderRegistry(List.of(broken, noType));

        assertThat(registry.load("BROKEN", "1")).isNull();
        assertThat(registry.types()).containsExactly("BROKEN");
        assertThat(registry.contains(" ")).isFalse();
    }

    private static SnapshotLoader loader(String type, Function<String, Object> function) {
        return new SnapshotLoader() {
            @Override
            public String type() {
                return type;
            }

            @Override
            public Object load(String id) {
                return function.apply(id);
            }
        };
    }
}
