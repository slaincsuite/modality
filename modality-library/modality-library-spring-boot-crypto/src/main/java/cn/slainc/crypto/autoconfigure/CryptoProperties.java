package cn.slainc.crypto.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "modality.crypto")
public class CryptoProperties {

    private boolean enabled = true;

    private String algorithm = "SM4";

    private String currentKeyId = "default";

    private boolean generateWhenMissing = true;

    private Map<String, String> keys = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public String getCurrentKeyId() {
        return currentKeyId;
    }

    public void setCurrentKeyId(String currentKeyId) {
        this.currentKeyId = currentKeyId;
    }

    public boolean isGenerateWhenMissing() {
        return generateWhenMissing;
    }

    public void setGenerateWhenMissing(boolean generateWhenMissing) {
        this.generateWhenMissing = generateWhenMissing;
    }

    public Map<String, String> getKeys() {
        return keys;
    }

    public void setKeys(Map<String, String> keys) {
        this.keys = keys;
    }
}
