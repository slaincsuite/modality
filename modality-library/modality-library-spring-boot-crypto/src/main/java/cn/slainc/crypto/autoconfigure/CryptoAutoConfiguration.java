package cn.slainc.crypto.autoconfigure;

import cn.slainc.crypto.key.CryptoKeyProvider;
import cn.slainc.crypto.key.EnvelopeEncryption;
import cn.slainc.crypto.key.MapCryptoKeyProvider;
import cn.slainc.crypto.key.SecretKeyUtils;
import cn.slainc.crypto.symmetric.AesKeySize;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.crypto.SecretKey;
import java.util.LinkedHashMap;
import java.util.Map;

@AutoConfiguration
@ConditionalOnClass(CryptoKeyProvider.class)
@ConditionalOnProperty(prefix = "modality.crypto", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(CryptoProperties.class)
public class CryptoAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CryptoAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public CryptoKeyProvider cryptoKeyProvider(CryptoProperties properties) {
        if (!properties.getKeys().isEmpty()) {
            return MapCryptoKeyProvider.of(properties.getKeys(), properties.getCurrentKeyId(),
                    properties.getAlgorithm());
        }
        if (!properties.isGenerateWhenMissing()) {
            throw new IllegalStateException("未配置 modality.crypto.keys 且已禁止自动生成密钥");
        }
        String keyId = properties.getCurrentKeyId();
        SecretKey generated = generate(properties.getAlgorithm());
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put(keyId, SecretKeyUtils.encodeBase64(generated));
        log.warn("未配置 modality.crypto.keys，已生成临时 {} 密钥 [{}]；进程重启后该密钥失效，生产环境请显式配置",
                properties.getAlgorithm(), keyId);
        return MapCryptoKeyProvider.of(keys, keyId, properties.getAlgorithm());
    }

    @Bean
    @ConditionalOnMissingBean
    public EnvelopeEncryption envelopeEncryption(CryptoKeyProvider keyProvider) {
        return new EnvelopeEncryption(keyProvider.currentKey(), keyProvider.currentKeyId());
    }

    private static SecretKey generate(String algorithm) {
        return "SM4".equalsIgnoreCase(algorithm) ? SecretKeyUtils.sm4Key() : SecretKeyUtils.aesKey(AesKeySize.AES_256);
    }
}
