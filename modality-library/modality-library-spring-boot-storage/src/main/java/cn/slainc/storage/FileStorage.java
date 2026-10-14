package cn.slainc.storage;

import cn.slainc.storage.spi.StorageMetadata;
import cn.slainc.storage.spi.StorageObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public interface FileStorage {

    String backend();

    void upload(String key, InputStream data, long size, String contentType);

    InputStream download(String key);

    void download(String key, OutputStream target);

    boolean delete(String key);

    boolean exists(String key);

    StorageMetadata metadata(String key);

    List<StorageObject> list(String prefix);
}
