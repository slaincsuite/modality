package cn.slainc.crypto.stream;

import cn.slainc.crypto.CryptoException;
import org.bouncycastle.crypto.BlockCipher;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.engines.SM4Engine;

public enum StreamCryptoAlgorithm {

    AES_GCM(1, "AES"),
    SM4_GCM(2, "SM4");

    private final int id;

    private final String keyAlgorithm;

    StreamCryptoAlgorithm(int id, String keyAlgorithm) {
        this.id = id;
        this.keyAlgorithm = keyAlgorithm;
    }

    public int id() {
        return id;
    }

    public String keyAlgorithm() {
        return keyAlgorithm;
    }

    public void validateKeyLength(int length) {
        if (SM4_GCM == this && length != 16) {
            throw new CryptoException("SM4 密钥长度必须为 16 字节");
        }
        if (AES_GCM == this && length != 16 && length != 24 && length != 32) {
            throw new CryptoException("AES 密钥长度必须为 16、24 或 32 字节");
        }
    }

    public static StreamCryptoAlgorithm fromId(int id) {
        for (StreamCryptoAlgorithm algorithm : values()) {
            if (algorithm.id == id) {
                return algorithm;
            }
        }
        throw new CryptoException("不支持的流式加密算法标识：" + id);
    }

    BlockCipher newCipher() {
        return AES_GCM == this ? AESEngine.newInstance() : new SM4Engine();
    }
}
