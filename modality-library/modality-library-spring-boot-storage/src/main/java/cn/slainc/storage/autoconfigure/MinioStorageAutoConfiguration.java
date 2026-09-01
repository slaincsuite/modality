package cn.slainc.storage.autoconfigure;

import cn.slainc.storage.minio.MinioFileStorage;
import cn.slainc.storage.minio.MinioStorageFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(name = "io.minio.MinioClient")
@ConditionalOnProperty(prefix = "modality.storage.minio", name = "enabled", havingValue = "true")
public class MinioStorageAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MinioFileStorage minioFileStorage(StorageProperties properties) {
        return MinioStorageFactory.create("minio", properties.getMinio());
    }
}
