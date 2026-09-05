package cn.slainc.sensitive.core;

public record SensitiveConfig(char maskChar, String replacement, String hashAlgorithm, boolean cycleSafe) {

    public static final char DEFAULT_MASK_CHAR = '*';

    public static final String DEFAULT_REPLACEMENT = "[已隐藏]";

    public static final String HASH_SM3 = "SM3";

    public static final String HASH_SHA256 = "SHA-256";

    public SensitiveConfig {
        maskChar = maskChar == 0 ? DEFAULT_MASK_CHAR : maskChar;
        replacement = replacement == null || replacement.isEmpty() ? DEFAULT_REPLACEMENT : replacement;
        hashAlgorithm = hashAlgorithm == null || hashAlgorithm.isBlank() ? HASH_SM3 : hashAlgorithm;
    }

    public static SensitiveConfig defaults() {
        return new SensitiveConfig(DEFAULT_MASK_CHAR, DEFAULT_REPLACEMENT, HASH_SM3, true);
    }
}
