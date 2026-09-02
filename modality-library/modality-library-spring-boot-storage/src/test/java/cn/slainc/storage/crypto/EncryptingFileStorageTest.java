package cn.slainc.storage.crypto;

import cn.slainc.crypto.key.EnvelopeEncryption;
import cn.slainc.crypto.key.SecretKeyUtils;
import cn.slainc.crypto.symmetric.AesKeySize;
import cn.slainc.storage.StorageException;
import cn.slainc.storage.local.LocalFileStorage;
import cn.slainc.storage.model.StorageMetadata;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptingFileStorageTest {

    @TempDir
    Path root;

    private LocalFileStorage local;

    private EncryptingFileStorage encrypted;

    private final byte[] kekBytes = new byte[32];

    @BeforeEach
    void setUp() {
        local = new LocalFileStorage("local", root, null);
        new java.util.Random(2026L).nextBytes(kekBytes);
        StorageKeyManager keyManager = new StorageKeyManager(
                new EnvelopeEncryption(SecretKeyUtils.aesKey(kekBytes), "test-kek"),
                root.resolve(".keys/dek.enc"));
        encrypted = new EncryptingFileStorage(local, keyManager);
    }

    @Test
    @DisplayName("透明加密：存储介质上的字节不等于原文，经 API 读回等于原文")
    void transparentEncryption() throws IOException {
        byte[] plain = "这是一段需要落盘加密的敏感内容，明文绝不应出现在磁盘上".getBytes(StandardCharsets.UTF_8);
        encrypted.upload("secret/a.txt", new ByteArrayInputStream(plain), plain.length, "text/plain");

        byte[] onDisk = Files.readAllBytes(root.resolve("secret/a.txt"));
        assertThat(onDisk).isNotEqualTo(plain);
        assertThat(new String(onDisk, StandardCharsets.UTF_8)).doesNotContain("敏感内容");
        assertThat(onDisk.length).isGreaterThan(plain.length);

        try (InputStream input = encrypted.download("secret/a.txt")) {
            assertThat(input.readAllBytes()).isEqualTo(plain);
        }
        StorageMetadata metadata = encrypted.metadata("secret/a.txt");
        assertThat(metadata.size()).isEqualTo(plain.length);
        assertThat(metadata.custom()).containsEntry("encrypted", "true");
    }

    @Test
    @DisplayName("DEK 落盘后复用：同一密钥管理器可解密既有对象")
    void dataKeyIsPersisted() throws IOException {
        byte[] plain = "跨会话可解密".getBytes(StandardCharsets.UTF_8);
        encrypted.upload("b.txt", new ByteArrayInputStream(plain), plain.length, "text/plain");
        assertThat(Files.exists(root.resolve(".keys/dek.enc"))).isTrue();

        StorageKeyManager reloaded = new StorageKeyManager(
                new EnvelopeEncryption(SecretKeyUtils.aesKey(kekBytes), "test-kek"),
                root.resolve(".keys/dek.enc"));
        EncryptingFileStorage second = new EncryptingFileStorage(local, reloaded);
        try (InputStream input = second.download("b.txt")) {
            assertThat(input.readAllBytes()).isEqualTo(plain);
        }
    }

    @Test
    @DisplayName("加密存储不支持分片上传并给出中文提示")
    void multipartRejected() {
        assertThat(encrypted.supportsMultipart()).isFalse();
        assertThatThrownBy(() -> encrypted.initMultipart("a.bin", "application/octet-stream"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("加密存储暂不支持分片上传");
    }

    @Test
    @DisplayName("用错误密钥解密会失败而不是返回脏数据")
    void wrongKeyFails() throws IOException {
        byte[] plain = "内容".getBytes(StandardCharsets.UTF_8);
        encrypted.upload("c.txt", new ByteArrayInputStream(plain), plain.length, "text/plain");
        byte[] otherKek = new byte[32];
        new java.util.Random(99L).nextBytes(otherKek);
        StorageKeyManager other = new StorageKeyManager(
                new EnvelopeEncryption(SecretKeyUtils.aesKey(otherKek), "other"),
                root.resolve(".keys/other.enc"));
        EncryptingFileStorage wrong = new EncryptingFileStorage(local, other);
        assertThatThrownBy(() -> {
            try (InputStream input = wrong.download("c.txt")) {
                input.readAllBytes();
            }
        }).isInstanceOf(RuntimeException.class);
    }
}
