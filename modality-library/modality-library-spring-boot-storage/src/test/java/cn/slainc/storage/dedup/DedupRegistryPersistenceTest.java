package cn.slainc.storage.dedup;

import cn.slainc.storage.local.LocalFileStorage;
import cn.slainc.storage.spi.UploadResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DedupRegistryPersistenceTest {

    @TempDir
    Path directory;

    @Test
    void referenceTableSurvivesRestartWithoutDeletingSharedObject() throws Exception {
        Path dataRoot = directory.resolve("data");
        Path registryRoot = directory.resolve("registry");
        byte[] content = "跨进程共享的物理对象".getBytes(StandardCharsets.UTF_8);

        InstantUploadService before = service(dataRoot, registryRoot);
        UploadResult first = before.upload("tenant/a/one.txt", new ByteArrayInputStream(content), content.length,
                "text/plain", null);
        UploadResult second = before.upload("tenant/a/two.txt", new ByteArrayInputStream(content), content.length,
                "text/plain", null);
        assertThat(first.instant()).isFalse();
        assertThat(second.instant()).isTrue();
        assertThat(second.referenceCount()).isEqualTo(2);

        InstantUploadService after = service(dataRoot, registryRoot);
        assertThat(after.referenceCount(second.md5())).isEqualTo(2);
        assertThat(after.exists("tenant/a/two.txt")).isTrue();
        try (InputStream input = after.download("tenant/a/two.txt")) {
            assertThat(input.readAllBytes()).isEqualTo(content);
        }

        assertThat(after.delete("tenant/a/two.txt")).isTrue();
        assertThat(Files.exists(dataRoot.resolve("tenant/a/one.txt"))).isTrue();
        try (InputStream input = after.download("tenant/a/one.txt")) {
            assertThat(input.readAllBytes()).isEqualTo(content);
        }

        assertThat(after.delete("tenant/a/one.txt")).isTrue();
        assertThat(Files.exists(dataRoot.resolve("tenant/a/one.txt"))).isFalse();
        assertThat(after.referenceCount(second.md5())).isZero();
    }

    @Test
    void sameDigestOnDifferentBackendsIsNotShared() throws Exception {
        SidecarDedupRegistry registry = new SidecarDedupRegistry(directory.resolve("registry"));
        byte[] content = "同摘要不同后端".getBytes(StandardCharsets.UTF_8);
        InstantUploadService firstBackend = new InstantUploadService(
                new LocalFileStorage("backend-a", directory.resolve("a"), null), new InstantUploadStats(), registry);
        InstantUploadService secondBackend = new InstantUploadService(
                new LocalFileStorage("backend-b", directory.resolve("b"), null), new InstantUploadStats(), registry);

        UploadResult first = firstBackend.upload("same.txt", new ByteArrayInputStream(content), content.length,
                "text/plain", null);
        UploadResult second = secondBackend.upload("same.txt", new ByteArrayInputStream(content), content.length,
                "text/plain", null);

        assertThat(first.instant()).isFalse();
        assertThat(second.instant()).isFalse();
        assertThat(Files.exists(directory.resolve("a/same.txt"))).isTrue();
        assertThat(Files.exists(directory.resolve("b/same.txt"))).isTrue();
    }

    private static InstantUploadService service(Path dataRoot, Path registryRoot) {
        return new InstantUploadService(new LocalFileStorage("local", dataRoot, null), new InstantUploadStats(),
                new SidecarDedupRegistry(registryRoot));
    }
}
