package cn.slainc.crypto.stream;

import cn.slainc.crypto.CryptoException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

final class StreamFormat {

    static final byte[] MAGIC = "MCRY".getBytes(StandardCharsets.US_ASCII);

    static final int VERSION = 1;

    static final int NONCE_LENGTH = 12;

    static final int TAG_LENGTH = 16;

    static final int HEADER_LENGTH = MAGIC.length + 1 + 1 + NONCE_LENGTH + 4;

    static final int DEFAULT_CHUNK_SIZE = 64 * 1024;

    static final int MIN_CHUNK_SIZE = 1024;

    static final int MAX_CHUNK_SIZE = 16 * 1024 * 1024;

    static final byte MORE_CHUNKS = 1;

    static final byte LAST_CHUNK = 0;

    private StreamFormat() {
    }

    static byte[] header(StreamCryptoAlgorithm algorithm, byte[] nonce, int chunkSize) {
        byte[] header = new byte[HEADER_LENGTH];
        System.arraycopy(MAGIC, 0, header, 0, MAGIC.length);
        int offset = MAGIC.length;
        header[offset++] = (byte) VERSION;
        header[offset++] = (byte) algorithm.id();
        System.arraycopy(nonce, 0, header, offset, NONCE_LENGTH);
        offset += NONCE_LENGTH;
        writeInt(header, offset, chunkSize);
        return header;
    }

    static void validateChunkSize(int chunkSize) {
        if (chunkSize < MIN_CHUNK_SIZE || chunkSize > MAX_CHUNK_SIZE) {
            throw new CryptoException("分块大小必须在 " + MIN_CHUNK_SIZE + " 与 " + MAX_CHUNK_SIZE + " 字节之间");
        }
    }

    static void writeAll(OutputStream output, byte[] data) throws IOException {
        output.write(data);
    }

    static byte[] readExactly(InputStream input, int length) throws IOException {
        byte[] data = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = input.read(data, offset, length - offset);
            if (read < 0) {
                throw new CryptoException("加密流已截断");
            }
            offset += read;
        }
        return data;
    }

    static void writeInt(byte[] target, int offset, int value) {
        target[offset] = (byte) (value >>> 24);
        target[offset + 1] = (byte) (value >>> 16);
        target[offset + 2] = (byte) (value >>> 8);
        target[offset + 3] = (byte) value;
    }

    static int readInt(byte[] source, int offset) {
        return (source[offset] & 0xFF) << 24
                | (source[offset + 1] & 0xFF) << 16
                | (source[offset + 2] & 0xFF) << 8
                | source[offset + 3] & 0xFF;
    }

    static byte[] chunkNonce(byte[] nonce, long counter) {
        byte[] chunkNonce = nonce.clone();
        for (int i = 0; i < 4; i++) {
            chunkNonce[NONCE_LENGTH - 1 - i] ^= (byte) (counter >>> (i * 8));
        }
        return chunkNonce;
    }

    static byte[] chunkAdditionalData(byte[] header, long counter) {
        byte[] additionalData = new byte[header.length + 4];
        System.arraycopy(header, 0, additionalData, 0, header.length);
        writeInt(additionalData, header.length, (int) counter);
        return additionalData;
    }
}
