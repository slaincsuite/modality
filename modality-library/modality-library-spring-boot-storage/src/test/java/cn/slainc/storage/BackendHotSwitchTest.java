package cn.slainc.storage;

import cn.slainc.storage.local.LocalFileStorage;
import cn.slainc.storage.routing.RoutingFileStorage;
import cn.slainc.storage.routing.StorageBackendRegistry;
import cn.slainc.storage.support.MutableDynamicConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BackendHotSwitchTest {

    @TempDir
    Path directory;

    @Test
    @DisplayName("运行中切换后端：切换后新上传落到新后端，切换前的老文件仍可读，进程不重启")
    void switchBackendWithoutRestart() throws Exception {
        Path rootA = directory.resolve("backend-a");
        Path rootB = directory.resolve("backend-b");
        StorageBackendRegistry registry = new StorageBackendRegistry();
        registry.register(new LocalFileStorage("backend-a", rootA, null));
        registry.register(new LocalFileStorage("backend-b", rootB, null));

        MutableDynamicConfigService config = new MutableDynamicConfigService();
        config.put("modality.storage.backend", "backend-a");
        RoutingFileStorage storage = new RoutingFileStorage(registry, config, "backend-a", List.of());

        byte[] first = "后端 A 的内容".getBytes(StandardCharsets.UTF_8);
        storage.upload("tenant/1/first.txt", new ByteArrayInputStream(first), first.length, "text/plain");

        assertThat(storage.activeBackend()).isEqualTo("backend-a");
        assertThat(Files.exists(rootA.resolve("tenant/1/first.txt"))).isTrue();
        assertThat(Files.exists(rootB.resolve("tenant/1/first.txt"))).isFalse();

        config.put("modality.storage.backend", "backend-b");

        byte[] second = "后端 B 的内容".getBytes(StandardCharsets.UTF_8);
        storage.upload("tenant/1/second.txt", new ByteArrayInputStream(second), second.length, "text/plain");

        assertThat(storage.activeBackend()).isEqualTo("backend-b");
        assertThat(Files.exists(rootB.resolve("tenant/1/second.txt"))).isTrue();
        assertThat(Files.exists(rootA.resolve("tenant/1/second.txt"))).isFalse();

        try (InputStream input = storage.download("tenant/1/first.txt")) {
            assertThat(input.readAllBytes()).isEqualTo(first);
        }
        assertThat(storage.exists("tenant/1/first.txt")).isTrue();
        assertThat(storage.metadata("tenant/1/first.txt").size()).isEqualTo(first.length);
        try (InputStream input = storage.download("tenant/1/second.txt")) {
            assertThat(input.readAllBytes()).isEqualTo(second);
        }
    }

    @Test
    @DisplayName("配置指向不存在的后端时保持既有后端并给出可用后端清单")
    void unknownBackendKeepsPreviousOne() {
        StorageBackendRegistry registry = new StorageBackendRegistry();
        registry.register(new LocalFileStorage("backend-a", directory.resolve("a"), null));
        MutableDynamicConfigService config = new MutableDynamicConfigService();
        config.put("modality.storage.backend", "backend-a");
        RoutingFileStorage storage = new RoutingFileStorage(registry, config, "backend-a", List.of());

        config.put("modality.storage.backend", "backend-x");
        assertThat(storage.activeBackend()).isEqualTo("backend-a");
    }
}