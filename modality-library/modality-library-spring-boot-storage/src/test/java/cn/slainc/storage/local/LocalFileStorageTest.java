package cn.slainc.storage.local;

import cn.slainc.storage.StorageException;
import cn.slainc.storage.model.StorageMetadata;
import cn.slainc.storage.model.StorageObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFileStorageTest {

    @TempDir
    Path root;

    private LocalFileStorage storage;

    @BeforeEach
    void setUp() {
        storage = new LocalFileStorage("local", root, null);
    }

    @Test
    @DisplayName("上传下载删除与存在性判断")
    void uploadDownloadDelete() throws IOException {
        byte[] data = "本地存储内容".getBytes(StandardCharsets.UTF_8);
        storage.upload("tenant/2026/02/13/a.txt", new ByteArrayInputStream(data), data.length, "text/plain");

        assertThat(storage.exists("tenant/2026/02/13/a.txt")).isTrue();
        try (InputStream input = storage.download("tenant/2026/02/13/a.txt")) {
            assertThat(input.readAllBytes()).isEqualTo(data);
        }
        StorageMetadata metadata = storage.metadata("tenant/2026/02/13/a.txt");
        assertThat(metadata.size()).isEqualTo(data.length);
        assertThat(metadata.md5()).isNotBlank();
        assertThat(metadata.contentType()).isEqualTo("text/plain");

        List<StorageObject> objects = storage.list("tenant/");
        assertThat(objects).hasSize(1);
        assertThat(objects.get(0).key()).isEqualTo("tenant/2026/02/13/a.txt");

        assertThat(storage.delete("tenant/2026/02/13/a.txt")).isTrue();
        assertThat(storage.exists("tenant/2026/02/13/a.txt")).isFalse();
        assertThat(storage.delete("tenant/2026/02/13/a.txt")).isFalse();
    }

    @Test
    @DisplayName("路径穿越在本地实现上被拒绝且不会写到根目录之外")
    void pathTraversalRejected() {
        assertThatThrownBy(() -> storage.upload("../../evil.txt", new ByteArrayInputStream(new byte[0]), 0,
                "text/plain")).isInstanceOf(StorageException.class)
                .hasMessageContaining("上级目录引用");
        assertThatThrownBy(() -> storage.download("/etc/passwd")).isInstanceOf(StorageException.class)
                .hasMessageContaining("绝对路径");
        assertThat(root.getParent().resolve("evil.txt")).doesNotExist();
    }

    @Test
    @DisplayName("声明长度与实际长度不一致时拒绝写入")
    void rejectsSizeMismatch() {
        byte[] data = "abc".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> storage.upload("a/b.txt", new ByteArrayInputStream(data), 100, "text/plain"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("长度与声明不一致");
        assertThat(storage.exists("a/b.txt")).isFalse();
    }

    @Test
    @DisplayName("本地后端分片上传：初始化、逐片上传、列出已传分片、合并")
    void multipart() throws IOException {
        byte[] data = "0123456789abcdefghij".getBytes(StandardCharsets.UTF_8);
        String uploadId = storage.initMultipart("big/file.bin", "application/octet-stream");
        storage.uploadPart("big/file.bin", uploadId, 1, new ByteArrayInputStream(data, 0, 8), 8);
        storage.uploadPart("big/file.bin", uploadId, 2, new ByteArrayInputStream(data, 8, 8), 8);

        assertThat(storage.listParts("big/file.bin", uploadId)).hasSize(2);

        storage.uploadPart("big/file.bin", uploadId, 3, new ByteArrayInputStream(data, 16, 4), 4);
        storage.completeMultipart("big/file.bin", uploadId, storage.listParts("big/file.bin", uploadId));

        try (InputStream input = storage.download("big/file.bin")) {
            assertThat(input.readAllBytes()).isEqualTo(data);
        }
        assertThat(storage.list(null)).extracting(StorageObject::key).containsExactly("big/file.bin");
    }

    @Test
    @DisplayName("本地桶管理映射为根目录下的子目录")
    void bucketManagement() throws IOException {
        storage.createBucket("photos");
        assertThat(storage.bucketExists("photos")).isTrue();
        assertThat(storage.listBuckets()).contains("photos");
        assertThatCode(() -> storage.deleteBucket("photos")).doesNotThrowAnyException();
        storage.upload("photos/a.txt", new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8)), 1,
                "text/plain");
        assertThatThrownBy(() -> storage.deleteBucket("photos")).isInstanceOf(StorageException.class)
                .hasMessageContaining("桶非空");
        storage.delete("photos/a.txt");
        storage.deleteBucket("photos");
        assertThat(storage.bucketExists("photos")).isFalse();
    }

    @Test
    @DisplayName("本地后端不支持预签名 URL，调用时给出中文异常")
    void signedUrlUnsupported() {
        assertThat(storage.supportsSignedUrl()).isFalse();
        assertThatThrownBy(() -> storage.presignedDownloadUrl("a.txt", java.time.Duration.ofMinutes(5)))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Sign");
    }

    @Test
    @DisplayName("读取不存在的对象给出中文异常")
    void missingObject() {
        assertThatThrownBy(() -> storage.download("none.txt")).isInstanceOf(StorageException.class)
                .hasMessageContaining("文件不存在");
        assertThatThrownBy(() -> storage.metadata("none.txt")).isInstanceOf(StorageException.class)
                .hasMessageContaining("文件不存在");
    }

    @Test
    @DisplayName("多租户按日期分目录的 key 生成规则")
    void keyGeneration() {
        String key = cn.slainc.storage.support.StorageKeyGenerator.generate("photo.PNG", 7L,
                java.time.LocalDate.of(2026, 2, 13));
        assertThat(key).startsWith("7/2026/02/13/").endsWith(".png");
    }

    @Test
    @DisplayName("上传过程中不会在目标目录留下临时文件")
    void noTempResidue() throws IOException {
        byte[] data = "content".getBytes(StandardCharsets.UTF_8);
        storage.upload("a/b.txt", new ByteArrayInputStream(data), data.length, "text/plain");
        try (var stream = Files.list(root.resolve("a"))) {
            assertThat(stream.map(path -> path.getFileName().toString())).containsExactly("b.txt");
        }
    }
}
