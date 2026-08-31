package cn.slainc.storage.support;

import cn.slainc.crypto.stream.StreamCryptoAlgorithm;
import cn.slainc.storage.ExtendedFileStorage;
import cn.slainc.storage.crypto.EncryptingFileStorage;
import cn.slainc.storage.crypto.StorageKeyManager;

public class EncryptingStorageWrapper implements StorageWrapper {

    private final StorageKeyManager keyManager;

    public EncryptingStorageWrapper(StorageKeyManager keyManager) {
        this.keyManager = keyManager;
    }

    @Override
    public ExtendedFileStorage wrap(ExtendedFileStorage delegate) {
        return new EncryptingFileStorage(delegate, keyManager, StreamCryptoAlgorithm.AES_GCM);
    }
}
