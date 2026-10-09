package cn.slainc.storage.minio;

import cn.slainc.storage.model.PartInfo;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MinioFileStorageTest {

    @Test
    void unknownSizePartUploadsFullContent() throws Exception {
        MinioClient client = mock(MinioClient.class);
        MinioFileStorage storage = new MinioFileStorage("minio", client, "bucket");
        byte[] content = "长度未知的分片内容".getBytes(StandardCharsets.UTF_8);

        PartInfo part = storage.uploadPart("big.bin", "upload0001", 1, new ByteArrayInputStream(content), -1);

        ArgumentCaptor<PutObjectArgs> captor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(client).putObject(captor.capture());
        assertThat(captor.getValue().objectSize()).isEqualTo(content.length);
        assertThat(part.size()).isEqualTo(content.length);
        assertThat(part.partNumber()).isEqualTo(1);
    }

    @Test
    void deleteReturnsFalseWhenObjectMissing() throws Exception {
        MinioClient client = mock(MinioClient.class);
        ErrorResponse missing = new ErrorResponse("NoSuchKey", "对象不存在", "bucket", "missing.txt", null, null, null);
        when(client.statObject(any(StatObjectArgs.class)))
                .thenThrow(new ErrorResponseException(missing, null, "test"));
        MinioFileStorage storage = new MinioFileStorage("minio", client, "bucket");

        assertThat(storage.delete("missing.txt")).isFalse();
    }
}
