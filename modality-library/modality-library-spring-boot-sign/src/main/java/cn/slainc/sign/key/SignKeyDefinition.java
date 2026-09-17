package cn.slainc.sign.key;

import cn.slainc.crypto.codec.Base64Utils;
import cn.slainc.sign.SignAlgorithm;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class SignKeyDefinition {

    private String appId;

    private String algorithm;

    private String secretBase64;

    private String publicKeyPem;

    private String privateKeyPem;

    private boolean enabled = true;

    private List<String> allowedIps = new ArrayList<>();

    public SignAlgorithm algorithmEnum() {
        return SignAlgorithm.fromName(algorithm);
    }

    // secretBase64 约定是「UTF-8 密钥文本」的 base64 编码，HMAC 密钥字节与 v1 拼接文本都由它派生
    public String secretText() {
        return Base64Utils.decodeToString(secretBase64);
    }

    public byte[] secretKey() {
        String text = secretText();
        return text == null ? new byte[0] : text.getBytes(StandardCharsets.UTF_8);
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public String getSecretBase64() {
        return secretBase64;
    }

    public void setSecretBase64(String secretBase64) {
        this.secretBase64 = secretBase64;
    }

    public String getPublicKeyPem() {
        return publicKeyPem;
    }

    public void setPublicKeyPem(String publicKeyPem) {
        this.publicKeyPem = publicKeyPem;
    }

    public String getPrivateKeyPem() {
        return privateKeyPem;
    }

    public void setPrivateKeyPem(String privateKeyPem) {
        this.privateKeyPem = privateKeyPem;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getAllowedIps() {
        return allowedIps;
    }

    public void setAllowedIps(List<String> allowedIps) {
        this.allowedIps = allowedIps;
    }
}
