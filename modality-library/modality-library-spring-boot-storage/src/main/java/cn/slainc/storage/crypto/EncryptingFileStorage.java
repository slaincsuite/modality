package cn.slainc.storage.crypto;

import cn.slainc.crypto.stream.DecryptingInputStream;
import cn.slainc.crypto.stream.StreamCrypto;
import cn.slainc.crypto.stream.StreamCryptoAlgorithm;
import cn.slainc.storage.ExtendedFileStorage;
import cn.slainc.storage.StorageException;
import cn.slainc.storage.spi.PartInfo;
import cn.slainc.storage.spi.StorageMetadata;
import cn.slainc.storage.support.DelegatingFileStorage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

public class EncryptingFileStorage extends DelegatingFileStorage {

    private final StorageKeyManager keyManager;

    private final StreamCryptoAlgorithm algorithm;

    public EncryptingFileStorage(ExtendedFileStorage delegate, StorageKeyManager keyManager) {
        this(delegate, keyManager, StreamCryptoAlgorithm.AES_GCM);
    }

    public EncryptingFileStorage(ExtendedFileStorage delegate, StorageKeyManager keyManager,
                                 StreamCryptoAlgorithm algorithm) {
        super(delegate);
        this.keyManager = keyManager;
        this.algorithm = algorithm;
    }

    public StorageKeyManager keyManager() {
        return keyManager;
    }

    @Override
    public void upload(String key, InputStream data, long size, String contentType) {
        Path temp = null;
        try {
            temp = Files.createTempFile("storage-enc-", ".tmp");
            try (OutputStream output = Files.newOutputStream(temp)) {
                StreamCrypto.encrypt(data, output, keyManager.dataKey(), algorithm);
            }
            try (InputStream encrypted = Files.newInputStream(temp)) {
                delegate().upload(key, encrypted, Files.size(temp), contentType);
            }
        } catch (IOException ex) {
            throw new StorageException("加密写入对象失败：" + key, ex);
        } finally {
            deleteQuietly(temp);
        }
    }

    @Override
    public InputStream download(String key) {
        return new DecryptingInputStream(delegate().download(key), keyManager.dataKey());
    }

    @Override
    public void download(String key, OutputStream target) {
        try (InputStream input = download(key)) {
            input.transferTo(target);
        } catch (IOException ex) {
            throw new StorageException("解密读取对象失败：" + key, ex);
        }
    }

    @Override
    public StorageMetadata metadata(String key) {
        StorageMetadata physical = delegate().metadata(key);
        return StorageMetadata.builder(key)
                .size(plainSize(key))
                .contentType(physical.contentType())
                .md5(physical.md5())
                .etag(physical.etag())
                .createdAt(physical.createdAt())
                .lastModified(physical.lastModified())
                .custom("encrypted", "true")
                .custom("keyId", keyManager.keyId())
                .custom("cipherSize", String.valueOf(physical.size()))
                .build();
    }

    @Override
    public boolean supportsMultipart() {
        return false;
    }

    @Override
    public String initMultipart(String key, String contentType) {
        throw new StorageException("加密存储暂不支持分片上传，请改用整体上传");
    }

    @Override
    public PartInfo uploadPart(String key, String uploadId, int partNumber, InputStream data, long size) {
        throw new StorageException("加密存储暂不支持分片上传，请改用整体上传");
    }

    @Override
    public void completeMultipart(String key, String uploadId, List<PartInfo> parts) {
        throw new StorageException("加密存储暂不支持分片上传，请改用整体上传");
    }

    public long plainSize(String key) {
        long total = 0L;
        try (InputStream input = download(key)) {
            total = input.transferTo(OutputStream.nullOutputStream());
        } catch (IOException ex) {
            throw new StorageException("统计解密后对象长度失败：" + key, ex);
        }
        return total;
    }

    public void read(String key, Consumer<InputStream> consumer) {
        try (InputStream input = download(key)) {
            consumer.accept(input);
        } catch (IOException ex) {
            throw new StorageException("读取加密对象失败：" + key, ex);
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            path.toFile().deleteOnExit();
        }
    }
}
