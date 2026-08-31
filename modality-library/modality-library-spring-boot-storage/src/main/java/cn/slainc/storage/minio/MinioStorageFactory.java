package cn.slainc.storage.minio;

import cn.slainc.storage.StorageException;
import cn.slainc.storage.autoconfigure.StorageProperties;
import io.minio.MinioClient;

public final class MinioStorageFactory {

    private MinioStorageFactory() {
    }

    public static MinioFileStorage create(String backend, StorageProperties.Minio properties) {
        if (properties.getEndpoint() == null || properties.getEndpoint().isBlank()) {
            throw new StorageException("启用 MinIO 后端必须配置 modality.storage.minio.endpoint");
        }
        MinioClient.Builder builder = MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey());
        if (properties.getRegion() != null && !properties.getRegion().isBlank()) {
            builder.region(properties.getRegion());
        }
        return new MinioFileStorage(backend, builder.build(), properties.getBucket());
    }
}
