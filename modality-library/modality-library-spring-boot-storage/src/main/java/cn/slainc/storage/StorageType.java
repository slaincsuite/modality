package cn.slainc.storage;

public enum StorageType {

    LOCAL("local"),
    S3("s3"),
    MINIO("minio"),
    CUSTOM("custom");

    private final String key;

    StorageType(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static StorageType fromKey(String key) {
        for (StorageType type : values()) {
            if (type.key.equalsIgnoreCase(key)) {
                return type;
            }
        }
        throw new StorageException("不支持的对象存储后端类型：" + key);
    }
}
