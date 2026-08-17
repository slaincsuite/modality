package cn.slainc.crypto.stream;

import cn.slainc.crypto.CryptoException;
import cn.slainc.crypto.random.SecureRandomUtils;
import java.io.IOException;
import java.io.OutputStream;
import javax.crypto.SecretKey;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.modes.GCMBlockCipher;
import org.bouncycastle.crypto.modes.GCMModeCipher;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.KeyParameter;

public final class EncryptingOutputStream extends OutputStream {

    private final OutputStream target;

    private final SecretKey key;

    private final StreamCryptoAlgorithm algorithm;

    private final int chunkSize;

    private final byte[] buffer;

    private byte[] header;

    private byte[] nonce;

    private int position;

    private long counter;

    private boolean closed;

    public EncryptingOutputStream(OutputStream target, SecretKey key, StreamCryptoAlgorithm algorithm) {
        this(target, key, algorithm, StreamFormat.DEFAULT_CHUNK_SIZE);
    }

    public EncryptingOutputStream(OutputStream target, SecretKey key, StreamCryptoAlgorithm algorithm, int chunkSize) {
        if (target == null || key == null) {
            throw new CryptoException("输出流与密钥不能为空");
        }
        byte[] keyBytes = key.getEncoded();
        algorithm.validateKeyLength(keyBytes.length);
        StreamFormat.validateChunkSize(chunkSize);
        this.target = target;
        this.key = key;
        this.algorithm = algorithm;
        this.chunkSize = chunkSize;
        this.buffer = new byte[chunkSize];
    }

    @Override
    public void write(int value) throws IOException {
        write(new byte[]{(byte) value}, 0, 1);
    }

    @Override
    public void write(byte[] data, int offset, int length) throws IOException {
        if (closed) {
            throw new IOException("加密流已关闭");
        }
        if (data == null) {
            throw new IOException("写入数据不能为空");
        }
        if (offset < 0 || length < 0 || offset + length > data.length) {
            throw new IndexOutOfBoundsException("非法的写入范围");
        }
        int remaining = length;
        int current = offset;
        while (remaining > 0) {
            int copied = Math.min(remaining, chunkSize - position);
            System.arraycopy(data, current, buffer, position, copied);
            position += copied;
            current += copied;
            remaining -= copied;
            if (position == chunkSize) {
                flushChunk(false);
            }
        }
    }

    @Override
    public void flush() throws IOException {
        target.flush();
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        try {
            flushChunk(true);
            target.flush();
        } finally {
            target.close();
        }
    }

    public int chunkSize() {
        return chunkSize;
    }

    public long chunkCount() {
        return counter;
    }

    private void flushChunk(boolean last) throws IOException {
        ensureHeader();
        byte[] plain = new byte[position + 1];
        plain[0] = last ? StreamFormat.LAST_CHUNK : StreamFormat.MORE_CHUNKS;
        System.arraycopy(buffer, 0, plain, 1, position);
        byte[] cipherText = process(plain);
        byte[] length = new byte[4];
        StreamFormat.writeInt(length, 0, cipherText.length);
        StreamFormat.writeAll(target, length);
        StreamFormat.writeAll(target, cipherText);
        position = 0;
        counter++;
    }

    private void ensureHeader() throws IOException {
        if (header != null) {
            return;
        }
        nonce = SecureRandomUtils.nextBytes(StreamFormat.NONCE_LENGTH);
        header = StreamFormat.header(algorithm, nonce, chunkSize);
        StreamFormat.writeAll(target, header);
    }

    private byte[] process(byte[] plain) {
        GCMModeCipher cipher = GCMBlockCipher.newInstance(algorithm.newCipher());
        cipher.init(true, new AEADParameters(new KeyParameter(key.getEncoded()), StreamFormat.TAG_LENGTH * 8,
                StreamFormat.chunkNonce(nonce, counter), StreamFormat.chunkAdditionalData(header, counter)));
        byte[] output = new byte[cipher.getOutputSize(plain.length)];
        int length = cipher.processBytes(plain, 0, plain.length, output, 0);
        try {
            cipher.doFinal(output, length);
        } catch (InvalidCipherTextException exception) {
            throw new CryptoException("流式加密失败", exception);
        }
        return output;
    }
}
