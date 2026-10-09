package cn.slainc.storage.routing;

import cn.slainc.storage.ExtendedFileStorage;
import cn.slainc.storage.StorageException;
import cn.slainc.storage.local.LocalFileStorage;
import cn.slainc.storage.support.DelegatingFileStorage;
import cn.slainc.storage.support.StorageWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RoutingFileStorageDecorateTest {

    @TempDir
    Path root;

    @Test
    void readPathGoesThroughDecorationChain() throws Exception {
        StorageBackendRegistry registry = new StorageBackendRegistry();
        registry.register(new LocalFileStorage("local", root, null));
        RoutingFileStorage storage = new RoutingFileStorage(registry, null, "local", List.of(new TransformWrapper()));
        byte[] content = "装饰链内容".getBytes(StandardCharsets.UTF_8);

        storage.upload("dir/object.txt", new ByteArrayInputStream(content), content.length, "text/plain");

        try (InputStream input = storage.download("dir/object.txt")) {
            assertThat(input.readAllBytes()).isEqualTo(content);
        }
        assertThat(storage.exists("dir/object.txt")).isTrue();
        assertThat(storage.metadata("dir/object.txt").size()).isEqualTo(content.length);
        assertThat(storage.delete("dir/object.txt")).isTrue();
        assertThat(storage.exists("dir/object.txt")).isFalse();
    }

    private static final class TransformWrapper implements StorageWrapper {

        @Override
        public ExtendedFileStorage wrap(ExtendedFileStorage delegate) {
            return new TransformStorage(delegate);
        }
    }

    private static final class TransformStorage extends DelegatingFileStorage {

        private TransformStorage(ExtendedFileStorage delegate) {
            super(delegate);
        }

        @Override
        public void upload(String key, InputStream data, long size, String contentType) {
            try {
                delegate().upload(key, new ByteArrayInputStream(xor(data.readAllBytes())), size, contentType);
            } catch (IOException ex) {
                throw new StorageException("测试包装器写入失败", ex);
            }
        }

        @Override
        public InputStream download(String key) {
            try (InputStream input = delegate().download(key)) {
                return new ByteArrayInputStream(xor(input.readAllBytes()));
            } catch (IOException ex) {
                throw new StorageException("测试包装器读取失败", ex);
            }
        }
    }

    private static byte[] xor(byte[] data) {
        byte[] result = new byte[data.length];
        for (int index = 0; index < data.length; index++) {
            result[index] = (byte) (data[index] ^ 0x5A);
        }
        return result;
    }
}
