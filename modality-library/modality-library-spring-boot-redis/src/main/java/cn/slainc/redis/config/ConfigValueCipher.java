package cn.slainc.redis.config;

public interface ConfigValueCipher {

    String encrypt(String plainText);

    String decrypt(String cipherText);
}
