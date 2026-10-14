package cn.slainc.storage;

import cn.slainc.storage.spi.PartInfo;

import java.io.InputStream;
import java.util.List;

public interface MultipartFileStorage {

    boolean supportsMultipart();

    String initMultipart(String key, String contentType);

    PartInfo uploadPart(String key, String uploadId, int partNumber, InputStream data, long size);

    List<PartInfo> listParts(String key, String uploadId);

    void completeMultipart(String key, String uploadId, List<PartInfo> parts);

    void abortMultipart(String key, String uploadId);
}
