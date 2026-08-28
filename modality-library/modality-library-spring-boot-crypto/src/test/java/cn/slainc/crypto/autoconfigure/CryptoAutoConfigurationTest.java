package cn.slainc.crypto.autoconfigure;

import cn.slainc.crypto.key.CryptoKeyProvider;
import cn.slainc.crypto.key.EnvelopeEncryption;
import cn.slainc.crypto.key.SecretKeyUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class CryptoAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CryptoAutoConfiguration.class));

    @Test
    @DisplayName("未配置密钥时自动生成临时密钥并装配能力")
    void generatesEphemeralKeyWhenMissing() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(CryptoKeyProvider.class);
            assertThat(context).hasSingleBean(EnvelopeEncryption.class);
            CryptoKeyProvider provider = context.getBean(CryptoKeyProvider.class);
            assertThat(provider.currentKey()).isNotNull();
            assertThat(provider.currentKeyId()).isEqualTo("default");
            assertThat(provider.currentKey().getAlgorithm()).isEqualTo("SM4");
        });
    }

    @Test
    @DisplayName("显式配置的密钥被采用且可完成信封加解密")
    void usesConfiguredKey() {
        SecretKey key = SecretKeyUtils.sm4Key();
        runner.withPropertyValues(
                        "modality.crypto.current-key-id=prod-1",
                        "modality.crypto.keys.prod-1=" + SecretKeyUtils.encodeBase64(key))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    CryptoKeyProvider provider = context.getBean(CryptoKeyProvider.class);
                    assertThat(provider.currentKeyId()).isEqualTo("prod-1");

                    EnvelopeEncryption encryption = context.getBean(EnvelopeEncryption.class);
                    byte[] cipher = encryption.encrypt("国密信封加密".getBytes(StandardCharsets.UTF_8));
                    byte[] plain = encryption.decrypt(cipher);
                    assertThat(new String(plain, StandardCharsets.UTF_8)).isEqualTo("国密信封加密");
                });
    }

    @Test
    @DisplayName("禁止自动生成且未配置密钥时启动失败并给出中文原因")
    void failsWhenKeyMissingAndGenerationDisabled() {
        runner.withPropertyValues("modality.crypto.generate-when-missing=false").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseMessage(
                    "未配置 modality.crypto.keys 且已禁止自动生成密钥");
        });
    }

    @Test
    @DisplayName("可通过 modality.crypto.enabled=false 整体关闭")
    void canBeDisabled() {
        runner.withPropertyValues("modality.crypto.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(CryptoKeyProvider.class);
        });
    }

    @Test
    @DisplayName("使用方提供的 Bean 覆盖默认实现")
    void userBeanOverridesDefault() {
        SecretKey key = SecretKeyUtils.sm4Key();
        runner.withBean(CryptoKeyProvider.class, () -> new CryptoKeyProvider() {
            @Override
            public SecretKey key(String keyId) {
                return key;
            }

            @Override
            public String currentKeyId() {
                return "custom";
            }
        }).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(CryptoKeyProvider.class).currentKeyId()).isEqualTo("custom");
        });
    }
}
