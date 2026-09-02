package cn.slainc.storage.lifecycle;

import cn.slainc.storage.local.LocalFileStorage;
import cn.slainc.storage.migration.MigrationReport;
import cn.slainc.storage.migration.StorageMigrator;
import cn.slainc.storage.routing.StorageBackendRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StorageLifecycleAndMigrationTest {

    @TempDir
    Path directory;

    @Test
    @DisplayName("过期规则清理超期对象，未超期对象保留")
    void expireRule() throws IOException {
        Path root = directory.resolve("hot");
        LocalFileStorage storage = new LocalFileStorage("local", root, null);
        upload(storage, "tmp/old.txt", "旧文件");
        upload(storage, "keep/new.txt", "新文件");
        Files.setLastModifiedTime(root.resolve("tmp/old.txt"), FileTime.from(Instant.now().minus(Duration.ofDays(10))));

        StorageLifecycleService service = new StorageLifecycleService(storage, new StorageBackendRegistry(),
                List.of(LifecycleRule.expire("临时文件", "tmp/", Duration.ofDays(7))));
        LifecycleReport report = service.run();

        assertThat(report.expired()).isEqualTo(1);
        assertThat(report.reclaimedBytes()).isEqualTo("旧文件".getBytes(StandardCharsets.UTF_8).length);
        assertThat(storage.exists("tmp/old.txt")).isFalse();
        assertThat(storage.exists("keep/new.txt")).isTrue();
    }

    @Test
    @DisplayName("归档规则把冷数据搬到冷存储后端后删除热数据")
    void archiveRule() throws IOException {
        Path hotRoot = directory.resolve("hot");
        Path coldRoot = directory.resolve("cold");
        LocalFileStorage hot = new LocalFileStorage("hot", hotRoot, null);
        LocalFileStorage cold = new LocalFileStorage("cold", coldRoot, null);
        StorageBackendRegistry registry = new StorageBackendRegistry();
        registry.register(hot);
        registry.register(cold);

        upload(hot, "archive/data.txt", "冷数据");
        Files.setLastModifiedTime(hotRoot.resolve("archive/data.txt"),
                FileTime.from(Instant.now().minus(Duration.ofDays(40))));

        StorageLifecycleService service = new StorageLifecycleService(hot, registry,
                List.of(new LifecycleRule("归档", "archive/", null, Duration.ofDays(30), "cold")));
        LifecycleReport report = service.run();

        assertThat(report.archived()).isEqualTo(1);
        assertThat(hot.exists("archive/data.txt")).isFalse();
        assertThat(cold.exists("archive/data.txt")).isTrue();
        try (InputStream input = cold.download("archive/data.txt")) {
            assertThat(new String(input.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("冷数据");
        }
    }

    @Test
    @DisplayName("后端间迁移工具按前缀搬运并可删除源对象")
    void migratorMovesObjects() throws IOException {
        Path sourceRoot = directory.resolve("source");
        Path targetRoot = directory.resolve("target");
        LocalFileStorage source = new LocalFileStorage("source", sourceRoot, null);
        LocalFileStorage target = new LocalFileStorage("target", targetRoot, null);
        StorageBackendRegistry registry = new StorageBackendRegistry();
        registry.register(source);
        registry.register(target);

        upload(source, "docs/a.txt", "甲");
        upload(source, "docs/b.txt", "乙");
        upload(source, "other/c.txt", "丙");

        MigrationReport report = new StorageMigrator(registry).migrate("source", "target", "docs/", true, null);

        assertThat(report.total()).isEqualTo(2);
        assertThat(report.succeeded()).isEqualTo(2);
        assertThat(report.failed()).isZero();
        assertThat(target.exists("docs/a.txt")).isTrue();
        assertThat(target.exists("other/c.txt")).isFalse();
        assertThat(source.exists("docs/a.txt")).isFalse();
    }

    private static void upload(LocalFileStorage storage, String key, String content) {
        byte[] data = content.getBytes(StandardCharsets.UTF_8);
        storage.upload(key, new ByteArrayInputStream(data), data.length, "text/plain");
    }
}
