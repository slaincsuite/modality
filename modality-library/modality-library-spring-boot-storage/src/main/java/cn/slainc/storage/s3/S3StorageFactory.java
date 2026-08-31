package cn.slainc.storage.s3;

import cn.slainc.storage.StorageException;
import cn.slainc.storage.autoconfigure.StorageProperties;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

public final class S3StorageFactory {

    private S3StorageFactory() {
    }

    public static S3FileStorage create(String backend, StorageProperties.S3 properties) {
        if (properties.getEndpoint() == null || properties.getEndpoint().isBlank()) {
            throw new StorageException("启用 S3 后端必须配置 modality.storage.s3.endpoint");
        }
        Region region = Region.of(properties.getRegion() == null ? "us-east-1" : properties.getRegion());
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey()));
        URI endpoint = URI.create(properties.getEndpoint());
        S3Configuration configuration = S3Configuration.builder()
                .pathStyleAccessEnabled(properties.isPathStyle())
                .build();
        S3Client client = S3Client.builder()
                .endpointOverride(endpoint)
                .region(region)
                .credentialsProvider(credentials)
                .serviceConfiguration(configuration)
                .build();
        S3Presigner presigner = null;
        if (properties.isPresignEnabled()) {
            presigner = S3Presigner.builder()
                    .endpointOverride(endpoint)
                    .region(region)
                    .credentialsProvider(credentials)
                    .serviceConfiguration(configuration)
                    .build();
        }
        return new S3FileStorage(backend, client, presigner, properties.getBucket());
    }
}
