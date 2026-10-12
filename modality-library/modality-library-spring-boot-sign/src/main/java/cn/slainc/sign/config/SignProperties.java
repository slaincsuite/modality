package cn.slainc.sign.config;

import cn.slainc.core.constant.HttpHeaders;
import cn.slainc.sign.SignAlgorithm;
import cn.slainc.sign.key.SignKeyDefinition;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "modality.sign")
public class SignProperties {

    private boolean enabled = true;

    private List<SignAlgorithm> allowedAlgorithms = new ArrayList<>(List.of(SignAlgorithm.values()));

    private long windowSeconds = 300;

    private int minNonceLength = 8;

    private boolean allowStoreFallback = true;

    private List<String> highSensitivityAppIds = new ArrayList<>();

    private OutputEncoding outputEncoding = OutputEncoding.BASE64;

    private Header header = new Header();

    private List<SignKeyDefinition> keys = new ArrayList<>();

    public enum OutputEncoding {
        BASE64,
        HEX
    }

    public static class Header {

        private String appId = "X-App-Id";

        private String timestamp = HttpHeaders.TIMESTAMP;

        private String nonce = HttpHeaders.NONCE;

        private String sign = "X-Sign";

        private String algorithm = "X-Sign-Algorithm";

        public String getAppId() {
            return appId;
        }

        public void setAppId(String appId) {
            this.appId = appId;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(String timestamp) {
            this.timestamp = timestamp;
        }

        public String getNonce() {
            return nonce;
        }

        public void setNonce(String nonce) {
            this.nonce = nonce;
        }

        public String getSign() {
            return sign;
        }

        public void setSign(String sign) {
            this.sign = sign;
        }

        public String getAlgorithm() {
            return algorithm;
        }

        public void setAlgorithm(String algorithm) {
            this.algorithm = algorithm;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<SignAlgorithm> getAllowedAlgorithms() {
        return allowedAlgorithms;
    }

    public void setAllowedAlgorithms(List<SignAlgorithm> allowedAlgorithms) {
        this.allowedAlgorithms = allowedAlgorithms;
    }

    public long getWindowSeconds() {
        return windowSeconds;
    }

    public void setWindowSeconds(long windowSeconds) {
        this.windowSeconds = windowSeconds;
    }

    public int getMinNonceLength() {
        return minNonceLength;
    }

    public void setMinNonceLength(int minNonceLength) {
        this.minNonceLength = minNonceLength;
    }

    public boolean isAllowStoreFallback() {
        return allowStoreFallback;
    }

    public void setAllowStoreFallback(boolean allowStoreFallback) {
        this.allowStoreFallback = allowStoreFallback;
    }

    public List<String> getHighSensitivityAppIds() {
        return highSensitivityAppIds;
    }

    public void setHighSensitivityAppIds(List<String> highSensitivityAppIds) {
        this.highSensitivityAppIds = highSensitivityAppIds == null ? new ArrayList<>() : highSensitivityAppIds;
    }

    public OutputEncoding getOutputEncoding() {
        return outputEncoding;
    }

    public void setOutputEncoding(OutputEncoding outputEncoding) {
        this.outputEncoding = outputEncoding;
    }

    public Header getHeader() {
        return header;
    }

    public void setHeader(Header header) {
        this.header = header;
    }

    public List<SignKeyDefinition> getKeys() {
        return keys;
    }

    public void setKeys(List<SignKeyDefinition> keys) {
        this.keys = keys;
    }
}
