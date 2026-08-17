package cn.slainc.crypto.stream;

import cn.slainc.crypto.CryptoException;
import java.io.IOException;
import java.io.InputStream;
import javax.crypto.SecretKey;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.modes.GCMBlockCipher;
import org.bouncycastle.crypto.modes.GCMModeCipher;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.KeyParameter;

public final class DecryptingInputStream extends InputStream {

    private final InputStream source;

    private final SecretKey key;

    private final StreamCryptoAlgorithm algorithm;

    private final byte[] header;

    private final byte[] nonce;

    private byte[] buffer = new byte[0];

    private int position;

    private long counter;

    private boolean finished;

    public DecryptingInputStream(InputStream source, SecretKey key) {
        if (source == null || key == null) {
            throw new CryptoException("输入流与密钥不能为空");
        }
        this.source = source;
        this.key = key;
        try {
            this.header = StreamFormat.readExactly(source, StreamFormat.HEADER_LENGTH);
        } catch (IOException exception) {
            throw new CryptoException("读取加密流头部失败", exception);
        }
        for (int i = 0; i < StreamFormat.MAGIC.length; i++) {
            if (header[i] != StreamFormat.MAGIC[i]) {
                throw new CryptoException("加密流头部标识不匹配");
            }
        }
        if ((header[StreamFormat.MAGIC.length] & 0xFF) != StreamFormat.VERSION) {
            throw new CryptoException("不支持的加密流版本：" + (header[StreamFormat.MAGIC.length] & 0xFF));
        }
        this.algorithm = StreamCryptoAlgorithm.fromId(header[StreamFormat.MAGIC.length + 1] & 0xFF);
        algorithm.validateKeyLength(key.getEncoded().length);
        this.nonce = new byte[StreamFormat.NONCE_LENGTH];
        System.arraycopy(header, StreamFormat.MAGIC.length + 2, nonce, 0, StreamFormat.NONCE_LENGTH);
    }

    @Override
    public int read() throws IOException {
        if (!ensureAvailable()) {
            return -1;
        }
        return buffer[position++] & 0xFF;
    }

    @Override
    public int read(byte[] target, int offset, int length) throws IOException {
        if (length == 0) {
            return 0;
        }
        if (!ensureAvailable()) {
            return -1;
        }
        int copied = Math.min(length, buffer.length - position);
        System.arraycopy(buffer, position, target, offset, copied);
        position += copied;
        return copied;
    }

    @Override
    public int available() {
        return buffer.length - position;
    }

    @Override
    public void close() throws IOException {
        source.close();
    }

    public StreamCryptoAlgorithm algorithm() {
        return algorithm;
    }

    public long chunkCount() {
        return counter;
    }

    private boolean ensureAvailable() throws IOException {
        while (position >= buffer.length) {
            if (finished) {
                return false;
            }
            readChunk();
        }
        return true;
    }

    private void readChunk() throws IOException {
        int first = source.read();
        if (first < 0) {
            throw new CryptoException("加密流已截断，缺少结束分块");
        }
        byte[] lengthBytes = new byte[4];
        lengthBytes[0] = (byte) first;
        byte[] remainder = StreamFormat.readExactly(source, 3);
        System.arraycopy(remainder, 0, lengthBytes, 1, 3);
        int length = StreamFormat.readInt(lengthBytes, 0);
        if (length <= StreamFormat.TAG_LENGTH || length > StreamFormat.MAX_CHUNK_SIZE + 1 + StreamFormat.TAG_LENGTH) {
            throw new CryptoException("非法的分块长度：" + length);
        }
        byte[] cipherText = StreamFormat.readExactly(source, length);
        byte[] plain = process(cipherText);
        if (plain.length == 0) {
            throw new CryptoException("非法的分块内容");
        }
        finished = plain[0] == StreamFormat.LAST_CHUNK;
        buffer = new byte[plain.length - 1];
        System.arraycopy(plain, 1, buffer, 0, buffer.length);
        position = 0;
        counter++;
    }

    private byte[] process(byte[] cipherText) {
        GCMModeCipher cipher = GCMBlockCipher.newInstance(algorithm.newCipher());
        cipher.init(false, new AEADParameters(new KeyParameter(key.getEncoded()), StreamFormat.TAG_LENGTH * 8,
                StreamFormat.chunkNonce(nonce, counter), StreamFormat.chunkAdditionalData(header, counter)));
        byte[] output = new byte[cipher.getOutputSize(cipherText.length)];
        int length = cipher.processBytes(cipherText, 0, cipherText.length, output, 0);
        try {
            cipher.doFinal(output, length);
        } catch (InvalidCipherTextException exception) {
            throw new CryptoException("流式解密失败，数据可能已被篡改", exception);
        }
        return output;
    }
}
