package cn.slainc.sign;

import cn.slainc.crypto.codec.Base64Utils;
import cn.slainc.sign.config.SignKeys;
import cn.slainc.sign.key.SignKeyDefinition;
import cn.slainc.sign.key.SignKeyRegistry;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SignKeyRegistryTest {

    @Test
    void findsKeyFromPropertyDefaults() {
        SignKeyRegistry registry = new SignKeyRegistry(
                SignTestSupport.properties(SignTestSupport.hmacKey("hmac-app", SignAlgorithm.HMAC_SHA256, "s1")),
                new SingleProvider<>(null));
        assertThat(registry.find("hmac-app")).isPresent();
        assertThat(registry.find("ghost")).isEmpty();
        assertThat(registry.find(null)).isEmpty();
        assertThat(registry.find(" ")).isEmpty();
    }

    @Test
    void dynamicConfigOverridesPropertyDefaults() {
        FakeConfigService config = new FakeConfigService();
        SignKeyRegistry registry = new SignKeyRegistry(
                SignTestSupport.properties(SignTestSupport.hmacKey("hmac-app", SignAlgorithm.HMAC_SHA256, "s1")),
                new SingleProvider<>(config));
        config.put(SignKeys.KEYS_CONFIG_KEY,
                "[{\"appId\":\"dyn-app\",\"algorithm\":\"HMAC_SM3\",\"secretBase64\":\"" + secret("s2") + "\"}]");
        assertThat(registry.find("dyn-app")).isPresent();
        assertThat(registry.find("dyn-app").orElseThrow().algorithmEnum()).isEqualTo(SignAlgorithm.HMAC_SM3);
        assertThat(registry.find("hmac-app")).isEmpty();
    }

    @Test
    void corruptedDynamicConfigFallsBackToDefaults() {
        FakeConfigService config = new FakeConfigService();
        SignKeyRegistry registry = new SignKeyRegistry(
                SignTestSupport.properties(SignTestSupport.hmacKey("hmac-app", SignAlgorithm.HMAC_SHA256, "s1")),
                new SingleProvider<>(config));
        config.put(SignKeys.KEYS_CONFIG_KEY, "不是 JSON");
        assertThat(registry.find("hmac-app")).isPresent();
    }

    @Test
    void hmacSecretCanBeFilledFromEncryptedChannel() {
        FakeConfigService config = new FakeConfigService();
        SignKeyRegistry registry = new SignKeyRegistry(SignTestSupport.properties(),
                new SingleProvider<>(config));
        config.put(SignKeys.KEYS_CONFIG_KEY, "[{\"appId\":\"enc-app\",\"algorithm\":\"HMAC_SHA256\"}]");
        config.put(SignKeys.secretConfigKey("enc-app"), secret("enc-secret"));
        Optional<SignKeyDefinition> found = registry.find("enc-app");
        assertThat(found).isPresent();
        assertThat(found.orElseThrow().secretText()).isEqualTo("enc-secret");
    }

    @Test
    void whitelistAllowsEverythingByDefault() {
        SignKeyRegistry registry = new SignKeyRegistry(SignTestSupport.properties(),
                new SingleProvider<>(null));
        for (SignAlgorithm algorithm : SignAlgorithm.values()) {
            assertThat(registry.isAlgorithmAllowed(algorithm)).as(algorithm.name()).isTrue();
        }
        assertThat(registry.isAlgorithmAllowed(null)).isFalse();
    }

    @Test
    void dynamicWhitelistBlocksDowngrade() {
        FakeConfigService config = new FakeConfigService();
        SignKeyRegistry registry = new SignKeyRegistry(SignTestSupport.properties(),
                new SingleProvider<>(config));
        config.put(SignKeys.ALLOWED_ALGORITHMS_CONFIG_KEY, "HMAC_SHA256");
        assertThat(registry.isAlgorithmAllowed(SignAlgorithm.HMAC_SHA256)).isTrue();
        assertThat(registry.isAlgorithmAllowed(SignAlgorithm.RSA_SHA256)).isFalse();
        assertThat(registry.isAlgorithmAllowed(SignAlgorithm.SM2)).isFalse();
    }

    @Test
    void dynamicWhitelistAcceptsJsonArrayAndChineseComma() {
        FakeConfigService config = new FakeConfigService();
        SignKeyRegistry registry = new SignKeyRegistry(SignTestSupport.properties(),
                new SingleProvider<>(config));
        config.put(SignKeys.ALLOWED_ALGORITHMS_CONFIG_KEY, "[\"HMAC_SHA256\",\"SM2\"]");
        assertThat(registry.isAlgorithmAllowed(SignAlgorithm.SM2)).isTrue();
        assertThat(registry.isAlgorithmAllowed(SignAlgorithm.RSA_SHA256)).isFalse();

        config.put(SignKeys.ALLOWED_ALGORITHMS_CONFIG_KEY, "HMAC_SM3，SM2");
        assertThat(registry.isAlgorithmAllowed(SignAlgorithm.HMAC_SM3)).isTrue();
        assertThat(registry.isAlgorithmAllowed(SignAlgorithm.SM2)).isTrue();
    }

    @Test
    void corruptedDynamicWhitelistFallsBackToDefaults() {
        FakeConfigService config = new FakeConfigService();
        SignKeyRegistry registry = new SignKeyRegistry(SignTestSupport.properties(),
                new SingleProvider<>(config));
        config.put(SignKeys.ALLOWED_ALGORITHMS_CONFIG_KEY, "[坏掉的 JSON");
        assertThat(registry.isAlgorithmAllowed(SignAlgorithm.RSA_SHA256)).isTrue();
    }

    private String secret(String text) {
        return Base64Utils.encode(text);
    }
}
