package cn.slainc.storage.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class UploadRequest {

    private String key;

    private String filename;

    private String contentType;

    private String md5;

    private long size = -1L;

    private Long tenantId;

    private Long uploaderId;

    private String uploaderName;

    private final Map<String, String> custom = new LinkedHashMap<>();

    public static UploadRequest of(String filename, String contentType) {
        return new UploadRequest().filename(filename).contentType(contentType);
    }

    public String key() {
        return key;
    }

    public UploadRequest key(String value) {
        this.key = value;
        return this;
    }

    public String filename() {
        return filename;
    }

    public UploadRequest filename(String value) {
        this.filename = value;
        return this;
    }

    public String contentType() {
        return contentType;
    }

    public UploadRequest contentType(String value) {
        this.contentType = value;
        return this;
    }

    public String md5() {
        return md5;
    }

    public UploadRequest md5(String value) {
        this.md5 = value;
        return this;
    }

    public long size() {
        return size;
    }

    public UploadRequest size(long value) {
        this.size = value;
        return this;
    }

    public Long tenantId() {
        return tenantId;
    }

    public UploadRequest tenantId(Long value) {
        this.tenantId = value;
        return this;
    }

    public Long uploaderId() {
        return uploaderId;
    }

    public UploadRequest uploaderId(Long value) {
        this.uploaderId = value;
        return this;
    }

    public String uploaderName() {
        return uploaderName;
    }

    public UploadRequest uploaderName(String value) {
        this.uploaderName = value;
        return this;
    }

    public Map<String, String> custom() {
        return custom;
    }

    public UploadRequest custom(String name, String value) {
        if (name != null) {
            custom.put(name, value);
        }
        return this;
    }
}
