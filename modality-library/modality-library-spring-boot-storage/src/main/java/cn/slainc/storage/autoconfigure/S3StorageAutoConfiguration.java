package cn.slainc.storage.autoconfigure;

import cn.slainc.storage.s3.S3FileStorage;
import cn.slainc.storage.s3.S3StorageFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(name = "software.amazon.awssdk.services.s3.S3Client")
@ConditionalOnProperty(prefix = "modality.storage.s3", name = "enabled", havingValue = "true")
public class S3StorageAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public S3FileStorage s3FileStorage(StorageProperties properties) {
        return S3StorageFactory.create("s3", properties.getS3());
    }
}
