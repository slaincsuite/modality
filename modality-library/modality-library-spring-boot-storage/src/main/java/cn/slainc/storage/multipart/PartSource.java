package cn.slainc.storage.multipart;

import java.io.IOException;
import java.io.InputStream;

@FunctionalInterface
public interface PartSource {

    InputStream open(int partNumber, long offset, long length) throws IOException;

    static PartSource of(java.nio.file.Path path) {
        return (partNumber, offset, length) -> {
            java.io.InputStream input = java.nio.file.Files.newInputStream(path);
            if (offset > 0) {
                input.skipNBytes(offset);
            }
            return input;
        };
    }

    static PartSource of(byte[] data) {
        return (partNumber, offset, length) -> new java.io.ByteArrayInputStream(data, (int) offset, (int) length);
    }
}
