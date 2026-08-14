package cn.slainc.crypto.hash;

public enum Pbkdf2Algorithm {

    PBKDF2_HMAC_SHA1("PBKDF2WithHmacSHA1", "pbkdf2-sha1"),
    PBKDF2_HMAC_SHA256("PBKDF2WithHmacSHA256", "pbkdf2-sha256"),
    PBKDF2_HMAC_SHA512("PBKDF2WithHmacSHA512", "pbkdf2-sha512");

    private final String jcaName;

    private final String label;

    Pbkdf2Algorithm(String jcaName, String label) {
        this.jcaName = jcaName;
        this.label = label;
    }

    public String jcaName() {
        return jcaName;
    }

    public String label() {
        return label;
    }

    public static Pbkdf2Algorithm fromLabel(String label) {
        for (Pbkdf2Algorithm algorithm : values()) {
            if (algorithm.label.equalsIgnoreCase(label)) {
                return algorithm;
            }
        }
        throw new IllegalArgumentException("不支持的 PBKDF2 算法：" + label);
    }
}
