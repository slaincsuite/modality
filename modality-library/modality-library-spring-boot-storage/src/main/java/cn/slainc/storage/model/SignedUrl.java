package cn.slainc.storage.model;

import java.time.Instant;

public record SignedUrl(String url, String method, String key, Instant expiresAt) {

    public boolean expired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
}
