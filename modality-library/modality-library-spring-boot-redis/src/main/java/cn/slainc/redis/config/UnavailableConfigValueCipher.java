package cn.slainc.redis.config;

public class UnavailableConfigValueCipher implements ConfigValueCipher {

    @Override
    public String encrypt(String plainText) {
        throw new IllegalStateException("未配置 CryptoKeyProvider，无法加密动态配置");
    }

    @Override
    public String decrypt(String cipherText) {
        throw new IllegalStateException("未配置 CryptoKeyProvider，无法解密动态配置");
    }
}
