package cn.slainc.storage.archive;

import java.io.IOException;
import java.io.InputStream;

public interface ZipEntrySource {

    String name();

    InputStream open() throws IOException;

    static ZipEntrySource of(String name, InputStream input) {
        return new ZipEntrySource() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public InputStream open() {
                return input;
            }
        };
    }

    static ZipEntrySource of(String name, byte[] data) {
        return of(name, new java.io.ByteArrayInputStream(data));
    }
}
