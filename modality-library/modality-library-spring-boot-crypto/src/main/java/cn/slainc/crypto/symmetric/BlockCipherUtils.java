package cn.slainc.crypto.symmetric;

import cn.slainc.crypto.CryptoException;
import java.util.Arrays;
import org.bouncycastle.crypto.BlockCipher;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.modes.GCMBlockCipher;
import org.bouncycastle.crypto.modes.GCMModeCipher;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.KeyParameter;

public final class BlockCipherUtils {

    public static final int GCM_TAG_BITS = 128;

    private BlockCipherUtils() {
    }

    public static byte[] encrypt(BlockCipher engine, CipherMode mode, Padding padding, byte[] key, byte[] iv, byte[] data) {
        return process(true, engine, mode, padding, key, iv, data);
    }

    public static byte[] decrypt(BlockCipher engine, CipherMode mode, Padding padding, byte[] key, byte[] iv, byte[] data) {
        return process(false, engine, mode, padding, key, iv, data);
    }

    private static byte[] process(boolean encrypt, BlockCipher engine, CipherMode mode, Padding padding, byte[] key, byte[] iv, byte[] data) {
        if (key == null || key.length == 0) {
            throw new CryptoException("密钥不能为空");
        }
        if (data == null) {
            throw new CryptoException("数据不能为空");
        }
        int blockSize = engine.getBlockSize();
        return switch (mode) {
            case GCM -> gcm(encrypt, engine, key, requireIv(iv), data);
            case CTR -> ctr(encrypt, engine, key, iv, requirePlain(padding, mode), data, blockSize);
            case CBC -> cbc(encrypt, engine, key, iv, padding, data, blockSize);
            case ECB -> ecb(encrypt, engine, key, padding, data, blockSize);
        };
    }

    private static Padding requirePlain(Padding padding, CipherMode mode) {
        if (padding.isPadded()) {
            throw new CryptoException(mode + " 模式不支持填充");
        }
        return padding;
    }

    private static byte[] ecb(boolean encrypt, BlockCipher engine, byte[] key, Padding padding, byte[] data, int blockSize) {
        byte[] input = encrypt ? pad(data, padding, blockSize) : data;
        requireAligned(input, blockSize);
        engine.init(encrypt, new KeyParameter(key));
        byte[] output = new byte[input.length];
        for (int offset = 0; offset < input.length; offset += blockSize) {
            engine.processBlock(input, offset, output, offset);
        }
        return encrypt ? output : unpad(output, padding, blockSize);
    }

    private static byte[] cbc(boolean encrypt, BlockCipher engine, byte[] key, byte[] iv, Padding padding, byte[] data, int blockSize) {
        byte[] chain = requireIv(iv, blockSize).clone();
        byte[] input = encrypt ? pad(data, padding, blockSize) : data;
        requireAligned(input, blockSize);
        engine.init(encrypt, new KeyParameter(key));
        byte[] output = new byte[input.length];
        byte[] buffer = new byte[blockSize];
        for (int offset = 0; offset < input.length; offset += blockSize) {
            if (encrypt) {
                for (int i = 0; i < blockSize; i++) {
                    buffer[i] = (byte) (input[offset + i] ^ chain[i]);
                }
                engine.processBlock(buffer, 0, output, offset);
                System.arraycopy(output, offset, chain, 0, blockSize);
            } else {
                engine.processBlock(input, offset, buffer, 0);
                for (int i = 0; i < blockSize; i++) {
                    output[offset + i] = (byte) (buffer[i] ^ chain[i]);
                }
                System.arraycopy(input, offset, chain, 0, blockSize);
            }
        }
        return encrypt ? output : unpad(output, padding, blockSize);
    }

    private static byte[] ctr(boolean encrypt, BlockCipher engine, byte[] key, byte[] iv, Padding padding, byte[] data, int blockSize) {
        byte[] counter = requireIv(iv, blockSize).clone();
        engine.init(true, new KeyParameter(key));
        byte[] output = new byte[data.length];
        byte[] keystream = new byte[blockSize];
        for (int offset = 0; offset < data.length; offset += blockSize) {
            engine.processBlock(counter, 0, keystream, 0);
            int length = Math.min(blockSize, data.length - offset);
            for (int i = 0; i < length; i++) {
                output[offset + i] = (byte) (data[offset + i] ^ keystream[i]);
            }
            increment(counter);
        }
        return output;
    }

    private static byte[] gcm(boolean encrypt, BlockCipher engine, byte[] key, byte[] iv, byte[] data) {
        GCMModeCipher cipher = GCMBlockCipher.newInstance(engine);
        cipher.init(encrypt, new AEADParameters(new KeyParameter(key), GCM_TAG_BITS, iv));
        byte[] output = new byte[cipher.getOutputSize(data.length)];
        int length = cipher.processBytes(data, 0, data.length, output, 0);
        try {
            cipher.doFinal(output, length);
        } catch (InvalidCipherTextException exception) {
            throw new CryptoException("无法使用 GCM " + (encrypt ? "加密" : "解密"), exception);
        }
        return output;
    }

    private static void increment(byte[] counter) {
        for (int i = counter.length - 1; i >= 0; i--) {
            if (++counter[i] != 0) {
                return;
            }
        }
    }

    private static byte[] pad(byte[] data, Padding padding, int blockSize) {
        if (!padding.isPadded()) {
            return data;
        }
        int length = blockSize - (data.length % blockSize);
        byte[] padded = Arrays.copyOf(data, data.length + length);
        Arrays.fill(padded, data.length, padded.length, (byte) length);
        return padded;
    }

    private static byte[] unpad(byte[] data, Padding padding, int blockSize) {
        if (!padding.isPadded()) {
            return data;
        }
        if (data.length == 0) {
            throw new CryptoException("填充数据不能为空");
        }
        int length = data[data.length - 1] & 0xFF;
        if (length == 0 || length > blockSize || length > data.length) {
            throw new CryptoException("非法的填充长度：" + length);
        }
        for (int i = data.length - length; i < data.length; i++) {
            if ((data[i] & 0xFF) != length) {
                throw new CryptoException("非法的填充内容");
            }
        }
        return Arrays.copyOf(data, data.length - length);
    }

    private static byte[] requireIv(byte[] iv) {
        if (iv == null || iv.length == 0) {
            throw new CryptoException("初始向量不能为空");
        }
        return iv;
    }

    private static byte[] requireIv(byte[] iv, int blockSize) {
        byte[] value = requireIv(iv);
        if (value.length != blockSize) {
            throw new CryptoException("初始向量长度必须为 " + blockSize + " 字节，实际为 " + value.length);
        }
        return value;
    }

    private static void requireAligned(byte[] data, int blockSize) {
        if (data.length % blockSize != 0) {
            throw new CryptoException("数据长度必须为 " + blockSize + " 的整数倍，实际为 " + data.length);
        }
    }
}
