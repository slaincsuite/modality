package cn.slainc.storage.model;

import java.time.Instant;

public record UploadResult(String key, String backend, long size, String md5, String contentType,
                           boolean instant, int referenceCount, Instant createdAt) {
}
