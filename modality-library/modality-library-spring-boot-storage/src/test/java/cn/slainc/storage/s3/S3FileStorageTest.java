package cn.slainc.storage.s3;

import cn.slainc.storage.StorageException;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class S3FileStorageTest {

    @Test
    void deleteReturnsFalseWhenObjectMissing() throws Exception {
        S3Client client = mock(S3Client.class);
        when(client.headObject(any(HeadObjectRequest.class))).thenThrow(NoSuchKeyException.builder().build());
        S3FileStorage storage = new S3FileStorage("s3", client, null, "bucket");

        assertThat(storage.delete("missing.txt")).isFalse();
        verify(client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void completeMultipartRejectsEmptyParts() {
        S3FileStorage storage = new S3FileStorage("s3", mock(S3Client.class), null, "bucket");

        assertThatThrownBy(() -> storage.completeMultipart("a.bin", "upload0001", List.of()))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("没有可合并的分片");
    }

    @Test
    void failureMessageToleratesMissingErrorDetails() throws Exception {
        S3Client client = mock(S3Client.class);
        when(client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().build());
        when(client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(S3Exception.builder().message("boom").statusCode(500).build());
        S3FileStorage storage = new S3FileStorage("s3", client, null, "bucket");

        assertThatThrownBy(() -> storage.delete("a.txt"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("未知");
    }
}
