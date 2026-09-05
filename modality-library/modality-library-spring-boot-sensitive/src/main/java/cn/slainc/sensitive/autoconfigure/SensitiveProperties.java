package cn.slainc.sensitive.autoconfigure;

import cn.slainc.sensitive.core.SensitiveConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "modality.sensitive")
public class SensitiveProperties {

    private boolean enabled = true;

    private final Mask mask = new Mask();

    private final Xss xss = new Xss();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Mask getMask() {
        return mask;
    }

    public Xss getXss() {
        return xss;
    }

    public static class Mask {

        private char maskChar = SensitiveConfig.DEFAULT_MASK_CHAR;

        private String replacement = SensitiveConfig.DEFAULT_REPLACEMENT;

        private String hashAlgorithm = SensitiveConfig.HASH_SM3;

        private boolean cycleSafe = true;

        public char getMaskChar() {
            return maskChar;
        }

        public void setMaskChar(char maskChar) {
            this.maskChar = maskChar;
        }

        public String getReplacement() {
            return replacement;
        }

        public void setReplacement(String replacement) {
            this.replacement = replacement;
        }

        public String getHashAlgorithm() {
            return hashAlgorithm;
        }

        public void setHashAlgorithm(String hashAlgorithm) {
            this.hashAlgorithm = hashAlgorithm;
        }

        public boolean isCycleSafe() {
            return cycleSafe;
        }

        public void setCycleSafe(boolean cycleSafe) {
            this.cycleSafe = cycleSafe;
        }
    }

    public static class Xss {

        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
