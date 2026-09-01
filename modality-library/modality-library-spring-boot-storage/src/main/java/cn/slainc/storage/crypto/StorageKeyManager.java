package cn.slainc.storage.crypto;

import cn.slainc.crypto.key.EnvelopeEncryption;
import cn.slainc.crypto.key.KeyWrapper;
import cn.slainc.crypto.key.SecretKeyUtils;
import cn.slainc.crypto.symmetric.AesKeySize;
import cn.slainc.storage.StorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

public class StorageKeyManager {

    private static final Logger log = LoggerFactory.getLogger(StorageKeyManager.class);

    private static final String KEY_FILE = ".keys/dek.enc";

    private final EnvelopeEncryption envelope;

    private final Path keyFile;

    private volatile SecretKey dataKey;

    public StorageKeyManager(EnvelopeEncryption envelope, Path keyFile) {
        this.envelope = envelope;
        this.keyFile = keyFile;
    }

    public String keyId() {
        return envelope.keyId();
    }

    public SecretKey dataKey() {
        SecretKey current = dataKey;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (dataKey == null) {
                dataKey = loadOrCreate();
            }
            return dataKey;
        }
    }

    public SecretKey kek() {
        return envelope.keyEncryptionKey();
    }

    public Path keyFile() {
        return keyFile;
    }

    private SecretKey loadOrCreate() {
        if (keyFile != null && Files.isRegularFile(keyFile)) {
            try {
                String text = Files.readString(keyFile, StandardCharsets.UTF_8).trim();
                String[] parts = text.split(":");
                if (parts.length == 3) {
                    return SecretKeyUtils.aesKey(KeyWrapper.unwrap(envelope.keyEncryptionKey(), decode(parts[2]),
                            parts[1]));
                }
                log.warn("DEK 文件格式非法，将重新生成：{}", keyFile);
            } catch (IOException ex) {
                throw new StorageException("读取数据加密密钥失败：" + keyFile, ex);
            }
        }
        SecretKey generated = SecretKeyUtils.aesKey(AesKeySize.AES_256);
        if (keyFile != null) {
            try {
                Files.createDirectories(keyFile.getParent());
                byte[] wrapped = KeyWrapper.wrap(envelope.keyEncryptionKey(), generated.getEncoded());
                String text = envelope.keyId() + ":" + KeyWrapper.AES_ALGORITHM + ":"
                        + Base64.getEncoder().encodeToString(wrapped);
                Files.writeString(keyFile, text, StandardCharsets.UTF_8);
            } catch (IOException ex) {
                throw new StorageException("写入数据加密密钥失败：" + keyFile, ex);
            }
        }
        log.info("已生成新的数据加密密钥（DEK），由 KEK [{}] 包裹后存放于 {}", envelope.keyId(), keyFile);
        return generated;
    }

    private static byte[] decode(String text) {
        return Base64.getDecoder().decode(text);
    }
}
