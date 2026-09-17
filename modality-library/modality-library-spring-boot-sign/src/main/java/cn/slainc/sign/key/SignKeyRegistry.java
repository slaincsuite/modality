package cn.slainc.sign.key;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.sign.SignAlgorithm;
import cn.slainc.sign.config.SignKeys;
import cn.slainc.sign.config.SignProperties;
import cn.slainc.utils.json.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SignKeyRegistry {

    private static final Logger log = LoggerFactory.getLogger(SignKeyRegistry.class);

    private final SignProperties properties;

    private final ObjectProvider<DynamicConfigService> configService;

    public SignKeyRegistry(SignProperties properties, ObjectProvider<DynamicConfigService> configService) {
        this.properties = properties;
        this.configService = configService;
    }

    public List<SignKeyDefinition> list() {
        DynamicConfigService service = configService == null ? null : configService.getIfAvailable();
        if (service != null) {
            String json = service.getString(SignKeys.KEYS_CONFIG_KEY, "");
            if (json != null && !json.isBlank()) {
                List<SignKeyDefinition> overridden = null;
                try {
                    overridden = JsonUtils.parseList(json, SignKeyDefinition.class);
                } catch (RuntimeException exception) {
                    log.warn("动态签名密钥配置不是合法 JSON，忽略并回退属性默认值：{}", exception.getMessage());
                }
                if (overridden != null && !overridden.isEmpty()) {
                    fillEncryptedSecrets(service, overridden);
                    return overridden;
                }
            }
        }
        return properties.getKeys() == null ? List.of() : properties.getKeys();
    }

    public Optional<SignKeyDefinition> find(String appId) {
        if (appId == null || appId.isBlank()) {
            return Optional.empty();
        }
        return list().stream().filter(key -> appId.equals(key.getAppId())).findFirst();
    }

    public boolean isAlgorithmAllowed(SignAlgorithm algorithm) {
        if (algorithm == null) {
            return false;
        }
        DynamicConfigService service = configService == null ? null : configService.getIfAvailable();
        if (service != null) {
            String value = service.getString(SignKeys.ALLOWED_ALGORITHMS_CONFIG_KEY, "");
            List<SignAlgorithm> allowed = parseAlgorithms(value);
            if (!allowed.isEmpty()) {
                return allowed.contains(algorithm);
            }
        }
        List<SignAlgorithm> defaults = properties.getAllowedAlgorithms();
        return defaults == null || defaults.isEmpty() || defaults.contains(algorithm);
    }

    private void fillEncryptedSecrets(DynamicConfigService service, List<SignKeyDefinition> keys) {
        for (SignKeyDefinition key : keys) {
            if (key.getSecretBase64() != null && !key.getSecretBase64().isBlank()) {
                continue;
            }
            try {
                String secret = service.getEncrypted(SignKeys.secretConfigKey(key.getAppId()));
                if (secret != null && !secret.isBlank()) {
                    key.setSecretBase64(secret);
                }
            } catch (RuntimeException exception) {
                log.warn("读取应用 {} 的加密密钥失败，按未配置处理：{}", key.getAppId(), exception.getMessage());
            }
        }
    }

    private List<SignAlgorithm> parseAlgorithms(String value) {
        List<SignAlgorithm> parsed = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return parsed;
        }
        String text = value.trim();
        if (text.startsWith("[")) {
            try {
                for (String name : JsonUtils.parseList(text, String.class)) {
                    SignAlgorithm algorithm = SignAlgorithm.fromName(name);
                    if (algorithm != null) {
                        parsed.add(algorithm);
                    }
                }
            } catch (RuntimeException exception) {
                log.warn("动态签名算法白名单不是合法 JSON，忽略：{}", exception.getMessage());
            }
            return parsed;
        }
        for (String name : text.split("[,，]")) {
            SignAlgorithm algorithm = SignAlgorithm.fromName(name);
            if (algorithm != null) {
                parsed.add(algorithm);
            }
        }
        return parsed;
    }
}
