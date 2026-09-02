package cn.slainc.storage.dedup;

import cn.slainc.storage.local.LocalFileStorage;
import cn.slainc.storage.model.StorageObject;
import cn.slainc.storage.model.UploadResult;
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

import static org.assertj.core.api.Assertions.assertThat;

class InstantUploadServiceTest {

    @TempDir
    Path root;

    private LocalFileStorage storage;

    private InstantUploadService service;

    @BeforeEach
    void setUp() {
        storage = new LocalFileStorage("local", root, null);
        service = new InstantUploadService(storage);
    }

    @Test
    @DisplayName("同内容第二次上传命中秒传，引用计数为 2；删一次仍可读，删两次才真正删除")
    void instantUploadWithReferenceCounting() throws IOException {
        byte[] data = "秒传内容".getBytes(StandardCharsets.UTF_8);
        UploadResult first = service.upload("a/first.txt", new ByteArrayInputStream(data), data.length, "text/plain",
                null);
        UploadResult second = service.upload("a/second.txt", new ByteArrayInputStream(data), data.length, "text/plain",
                null);

        assertThat(first.instant()).isFalse();
        assertThat(first.referenceCount()).isEqualTo(1);
        assertThat(second.instant()).isTrue();
        assertThat(second.referenceCount()).isEqualTo(2);
        assertThat(second.key()).isEqualTo(first.key());
        assertThat(service.referenceCount(second.md5())).isEqualTo(2);

        assertThat(Files.exists(root.resolve("a/first.txt"))).isTrue();
        assertThat(Files.exists(root.resolve("a/second.txt"))).isFalse();

        try (InputStream input = service.download("a/second.txt")) {
            assertThat(input.readAllBytes()).isEqualTo(data);
        }
        assertThat(service.exists("a/second.txt")).isTrue();
        assertThat(service.metadata("a/second.txt").key()).isEqualTo("a/second.txt");
        assertThat(service.metadata("a/second.txt").size()).isEqualTo(data.length);

        assertThat(service.delete("a/second.txt")).isTrue();
        assertThat(service.exists("a/first.txt")).isTrue();
        try (InputStream input = service.download("a/first.txt")) {
            assertThat(input.readAllBytes()).isEqualTo(data);
        }

        assertThat(service.delete("a/first.txt")).isTrue();
        assertThat(service.exists("a/first.txt")).isFalse();
        assertThat(storage.list(null)).extracting(StorageObject::key).isEmpty();
        assertThat(service.referenceCount(second.md5())).isZero();
    }

    @Test
    @DisplayName("不同内容不会误判为秒传")
    void differentContentNotDeduplicated() throws IOException {
        byte[] first = "内容一".getBytes(StandardCharsets.UTF_8);
        byte[] second = "内容二".getBytes(StandardCharsets.UTF_8);
        UploadResult resultA = service.upload("x.txt", new ByteArrayInputStream(first), first.length, "text/plain",
                null);
        UploadResult resultB = service.upload("y.txt", new ByteArrayInputStream(second), second.length, "text/plain",
                null);
        assertThat(resultA.instant()).isFalse();
        assertThat(resultB.instant()).isFalse();
        assertThat(resultA.md5()).isNotEqualTo(resultB.md5());
        assertThat(service.exists("x.txt")).isTrue();
        assertThat(service.exists("y.txt")).isTrue();
    }

    @Test
    @DisplayName("客户端已知 MD5 时直接走摘要比对，秒传成功率统计随之更新")
    void statsAreRecorded() {
        byte[] data = "统计内容".getBytes(StandardCharsets.UTF_8);
        String md5 = cn.slainc.utils.digest.DigestUtils.md5(data);
        service.upload("s1.txt", new ByteArrayInputStream(data), data.length, "text/plain", md5);
        service.upload("s2.txt", new ByteArrayInputStream(data), data.length, "text/plain", md5);
        service.upload("s3.txt", new ByteArrayInputStream("其它".getBytes(StandardCharsets.UTF_8)), 6, "text/plain",
                null);

        InstantUploadStats.InstantUploadStat stat = service.stats().snapshot();
        assertThat(stat.attempts()).isEqualTo(3);
        assertThat(stat.hits()).isEqualTo(1);
        assertThat(stat.hitRate()).isCloseTo(1d / 3d, org.assertj.core.data.Offset.offset(0.0001d));
        assertThat(stat.savedBytes()).isEqualTo(data.length);
    }

    @Test
    @DisplayName("秒传别名不会额外占用物理空间")
    void aliasDoesNotDuplicatePhysicalObject() throws IOException {
        byte[] data = new byte[4096];
        for (int index = 0; index < 10; index++) {
            service.upload("alias/" + index + ".bin", new ByteArrayInputStream(data), data.length,
                    "application/octet-stream", null);
        }
        assertThat(storage.list(null)).hasSize(1);
        assertThat(service.referenceCount(cn.slainc.utils.digest.DigestUtils.md5(data))).isEqualTo(10);
    }
}
