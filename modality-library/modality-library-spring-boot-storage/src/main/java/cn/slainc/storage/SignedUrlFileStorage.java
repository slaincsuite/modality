package cn.slainc.storage;

import java.time.Duration;

public interface SignedUrlFileStorage {

    boolean supportsSignedUrl();

    String presignedUploadUrl(String key, Duration expiry);

    String presignedDownloadUrl(String key, Duration expiry);
}
