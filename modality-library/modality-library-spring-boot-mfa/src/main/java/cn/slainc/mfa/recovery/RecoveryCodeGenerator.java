package cn.slainc.mfa.recovery;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

public class RecoveryCodeGenerator {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final int GROUP_LENGTH = 4;

    private static final int GROUP_COUNT = 2;

    private static final char SEPARATOR = '-';

    private final SecureRandom secureRandom;

    public RecoveryCodeGenerator() {
        this(new SecureRandom());
    }

    public RecoveryCodeGenerator(SecureRandom secureRandom) {
        this.secureRandom = secureRandom == null ? new SecureRandom() : secureRandom;
    }

    public List<String> generate(int count) {
        if (count <= 0) {
            return List.of();
        }
        List<String> codes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            codes.add(nextCode());
        }
        return codes;
    }

    public String hash(String code) {
        String normalized = normalize(code);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("恢复码不能为空");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", e);
        }
    }

    public boolean matches(String code, String storedHash) {
        if (code == null || storedHash == null) {
            return false;
        }
        String normalized = normalize(code);
        if (normalized.isEmpty()) {
            return false;
        }
        byte[] candidate = hash(normalized).getBytes(StandardCharsets.US_ASCII);
        byte[] expected = storedHash.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(candidate, expected);
    }

    public String normalize(String code) {
        if (code == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(code.length());
        for (int i = 0; i < code.length(); i++) {
            char current = code.charAt(i);
            if (current == SEPARATOR || Character.isWhitespace(current)) {
                continue;
            }
            builder.append(Character.toUpperCase(current));
        }
        return builder.toString();
    }

    private String nextCode() {
        StringBuilder builder = new StringBuilder(GROUP_LENGTH * GROUP_COUNT + GROUP_COUNT - 1);
        for (int group = 0; group < GROUP_COUNT; group++) {
            if (group > 0) {
                builder.append(SEPARATOR);
            }
            for (int i = 0; i < GROUP_LENGTH; i++) {
                builder.append(ALPHABET.charAt(secureRandom.nextInt(ALPHABET.length())));
            }
        }
        return builder.toString();
    }
}
