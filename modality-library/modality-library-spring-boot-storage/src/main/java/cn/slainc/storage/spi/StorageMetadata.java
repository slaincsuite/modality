package cn.slainc.storage.spi;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record StorageMetadata(String key, long size, String contentType, String md5, String etag,
                              Instant createdAt, Instant lastModified, Long tenantId, Long uploaderId,
                              String uploaderName, Map<String, String> custom, ImageMetadata image,
                              VideoMetadata video) {

    public StorageMetadata {
        custom = custom == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(custom));
    }

    public static Builder builder(String key) {
        return new Builder(key);
    }

    public static StorageMetadata basic(String key, long size, String contentType) {
        return builder(key).size(size).contentType(contentType).lastModified(Instant.now()).build();
    }

    public static final class Builder {

        private final String key;

        private long size;

        private String contentType;

        private String md5;

        private String etag;

        private Instant createdAt;

        private Instant lastModified;

        private Long tenantId;

        private Long uploaderId;

        private String uploaderName;

        private final Map<String, String> custom = new LinkedHashMap<>();

        private ImageMetadata image;

        private VideoMetadata video;

        private Builder(String key) {
            this.key = key;
        }

        public Builder size(long value) {
            this.size = value;
            return this;
        }

        public Builder contentType(String value) {
            this.contentType = value;
            return this;
        }

        public Builder md5(String value) {
            this.md5 = value;
            return this;
        }

        public Builder etag(String value) {
            this.etag = value;
            return this;
        }

        public Builder createdAt(Instant value) {
            this.createdAt = value;
            return this;
        }

        public Builder lastModified(Instant value) {
            this.lastModified = value;
            return this;
        }

        public Builder tenantId(Long value) {
            this.tenantId = value;
            return this;
        }

        public Builder uploaderId(Long value) {
            this.uploaderId = value;
            return this;
        }

        public Builder uploaderName(String value) {
            this.uploaderName = value;
            return this;
        }

        public Builder custom(String name, String value) {
            if (name != null) {
                this.custom.put(name, value);
            }
            return this;
        }

        public Builder custom(Map<String, String> values) {
            if (values != null) {
                this.custom.putAll(values);
            }
            return this;
        }

        public Builder image(ImageMetadata value) {
            this.image = value;
            return this;
        }

        public Builder video(VideoMetadata value) {
            this.video = value;
            return this;
        }

        public StorageMetadata build() {
            return new StorageMetadata(key, size, contentType, md5, etag, createdAt, lastModified, tenantId,
                    uploaderId, uploaderName, custom, image, video);
        }
    }
}
