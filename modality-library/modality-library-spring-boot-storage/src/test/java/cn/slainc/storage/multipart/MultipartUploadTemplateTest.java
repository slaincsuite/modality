package cn.slainc.storage.multipart;

import cn.slainc.storage.StorageException;
import cn.slainc.storage.local.LocalFileStorage;
import cn.slainc.storage.spi.PartInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MultipartUploadTemplateTest {

    @TempDir
    Path root;

    @Test
    @DisplayName("传一半 -> 查已传分片 -> 续传剩余 -> 合并，内容与原始字节一致")
    void resumeUpload() throws IOException {
        byte[] data = new byte[1024 * 64 + 123];
        new Random(42).nextBytes(data);
        LocalFileStorage storage = new LocalFileStorage("local", root, null);
        MultipartUploadTemplate template = new MultipartUploadTemplate(storage, 16 * 1024);

        MultipartSession session = template.init("big/file.bin", data.length, "application/octet-stream");
        assertThat(session.totalParts()).isEqualTo(5);
        template.uploadPart(session, 1, new java.io.ByteArrayInputStream(data, 0, 16 * 1024), 16 * 1024);
        template.uploadPart(session, 2, new java.io.ByteArrayInputStream(data, 16 * 1024, 16 * 1024), 16 * 1024);

        MultipartSession resumed = new MultipartUploadTemplate(storage, 16 * 1024)
                .resume(new MultipartSession("big/file.bin", session.uploadId(), data.length, 16 * 1024,
                        "application/octet-stream"));
        assertThat(resumed.uploadedPartCount()).isEqualTo(2);
        assertThat(resumed.pendingPartNumbers()).containsExactly(3, 4, 5);
        assertThat(template.listParts(session)).extracting(PartInfo::partNumber).containsExactly(1, 2);

        List<UploadProgress> progress = new ArrayList<>();
        template.uploadMissingParts(resumed, PartSource.of(data), progress::add);
        assertThat(progress).hasSize(3);
        assertThat(progress.get(2).percent()).isEqualTo(1d);
        assertThat(resumed.uploadedPartCount()).isEqualTo(5);

        template.complete(resumed);
        try (InputStream input = storage.download("big/file.bin")) {
            assertThat(input.readAllBytes()).isEqualTo(data);
        }
    }

    @Test
    @DisplayName("分片未传完就合并会被拒绝")
    void completeBeforeAllPartsUploaded() {
        byte[] data = new byte[64];
        LocalFileStorage storage = new LocalFileStorage("local", root, null);
        MultipartUploadTemplate template = new MultipartUploadTemplate(storage, 16);
        MultipartSession session = template.init("a.bin", data.length, "application/octet-stream");
        template.uploadPart(session, 1, new java.io.ByteArrayInputStream(data, 0, 16), 16);
        assertThatThrownBy(() -> template.complete(session)).isInstanceOf(StorageException.class)
                .hasMessageContaining("分片未上传完整");
    }

    @Test
    @DisplayName("多分片并发上传后合并，内容一致")
    void concurrentUpload() throws IOException {
        byte[] data = new byte[200 * 1024];
        new Random(7).nextBytes(data);
        LocalFileStorage storage = new LocalFileStorage("local", root, null);
        MultipartUploadTemplate template = new MultipartUploadTemplate(storage, 32 * 1024);
        MultipartSession session = template.init("concurrent.bin", data.length, "application/octet-stream");

        List<UploadProgress> progress = java.util.Collections.synchronizedList(new ArrayList<>());
        template.uploadConcurrently(session, PartSource.of(data), 4, progress::add);
        assertThat(progress).hasSize(7);
        template.complete(session);

        assertThat(storage.metadata("concurrent.bin").md5()).isEqualTo(cn.slainc.utils.digest.DigestUtils.md5(data));
    }

    @Test
    @DisplayName("终止分片上传后临时分片被清理")
    void abortUpload() {
        LocalFileStorage storage = new LocalFileStorage("local", root, null);
        MultipartUploadTemplate template = new MultipartUploadTemplate(storage, 16);
        MultipartSession session = template.init("abort.bin", 64, "application/octet-stream");
        template.uploadPart(session, 1, new java.io.ByteArrayInputStream(new byte[16]), 16);
        template.abort(session);
        assertThat(template.listParts(session)).isEmpty();
        assertThat(storage.exists("abort.bin")).isFalse();
    }

    @Test
    @DisplayName("分片序号必须从 1 开始")
    void rejectsInvalidPartNumber() {
        LocalFileStorage storage = new LocalFileStorage("local", root, null);
        String uploadId = storage.initMultipart("a.bin", "application/octet-stream");
        assertThatThrownBy(() -> storage.uploadPart("a.bin", uploadId, 0,
                new java.io.ByteArrayInputStream(new byte[1]), 1))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("分片序号必须从 1 开始");
    }

    @Test
    @DisplayName("分片上传标识带路径穿越时被拒绝")
    void rejectsUnsafeUploadId() {
        LocalFileStorage storage = new LocalFileStorage("local", root, null);
        assertThatThrownBy(() -> storage.listParts("a.bin", "../../etc"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("非法的分片上传标识");
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
