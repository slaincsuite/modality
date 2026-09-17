package cn.slainc.apikey;

import java.security.SecureRandom;

public class ApiKeyGenerator {

    private static final String ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private final SecureRandom random = new SecureRandom();

    private final String prefix;

    private final int length;

    public ApiKeyGenerator(String prefix, int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("API Key 随机段长度必须大于 0，当前为 " + length);
        }
        this.prefix = prefix == null ? "" : prefix;
        this.length = length;
    }

    public String next() {
        StringBuilder builder = new StringBuilder(prefix.length() + length);
        builder.append(prefix);
        for (int i = 0; i < length; i++) {
            builder.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return builder.toString();
    }
}
