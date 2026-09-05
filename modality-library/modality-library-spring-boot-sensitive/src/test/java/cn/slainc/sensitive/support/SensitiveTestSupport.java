package cn.slainc.sensitive.support;

import cn.slainc.crypto.key.CryptoKeyProvider;
import cn.slainc.crypto.key.MapCryptoKeyProvider;
import cn.slainc.crypto.key.SecretKeyUtils;
import cn.slainc.sensitive.core.SensitiveConfig;
import cn.slainc.sensitive.core.SensitiveMasker;
import cn.slainc.sensitive.core.SensitiveSwitch;
import cn.slainc.sensitive.jackson.SensitiveJacksonCustomizer;
import cn.slainc.sensitive.metrics.SensitiveMetrics;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

public final class SensitiveTestSupport {

    public static final SensitiveConfig CONFIG = SensitiveConfig.defaults();

    private SensitiveTestSupport() {
    }

    public static CryptoKeyProvider keyProvider() {
        return MapCryptoKeyProvider.of(
                Map.of("default", SecretKeyUtils.encodeBase64(SecretKeyUtils.sm4Key())), "default", "SM4");
    }

    public static SensitiveMasker masker(CryptoKeyProvider keyProvider, SensitiveMetrics metrics) {
        return new SensitiveMasker(CONFIG, metrics, new SensitiveSwitch(true, () -> null), () -> keyProvider);
    }

    public static JsonMapper mapper(SensitiveMasker masker) {
        JsonMapper.Builder builder = JsonMapper.builder();
        new SensitiveJacksonCustomizer(masker, CONFIG).customize(builder);
        return builder.build();
    }
}
