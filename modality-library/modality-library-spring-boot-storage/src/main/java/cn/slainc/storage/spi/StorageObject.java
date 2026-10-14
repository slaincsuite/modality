package cn.slainc.storage.spi;

import java.time.Instant;

public record StorageObject(String key, long size, String contentType, String etag, Instant lastModified) {
}
